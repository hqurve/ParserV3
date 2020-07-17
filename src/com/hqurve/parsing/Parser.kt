package com.hqurve.parsing

interface Result<out T>{
    fun asValue() = (this as ValueResult).value
    fun asChar() = (this as CharResult).char
    fun asString() = (this as StringResult).string
    fun asCompound() = this as CompoundResult
}

data class ValueResult<out T>(val value: T): Result<T>

data class CharResult<out T>(val char: Char): Result<T>
data class StringResult<out T>(val string: String): Result<T>

data class CompoundResult<out T>(val subResults: List<Result<T>>): Result<T>, Iterable<Result<T>>{
    constructor(vararg subs: Result<T>): this(subs.toList())

    val size: Int
        get() = subResults.size

    operator fun get(index: Int) = subResults[index]
    fun isEmpty() = subResults.isEmpty()
    fun isNotEmpty() = !isEmpty()
    override fun iterator() = subResults.iterator()

    fun valueAt(index: Int) = get(index).asValue()
    fun charAt(index: Int) = get(index).asChar()
    fun stringAt(index: Int) = get(index).asString()
    fun compoundAt(index: Int) = get(index).asCompound()
}





interface Parser<out T, in F>{
    class CharSequence(val string: String, val start: Int = 0, override val length: Int = string.length - start): kotlin.CharSequence{
        override fun get(index: Int): Char {
            return string[start + index]
        }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            return CharSequence(string, start + startIndex, endIndex - startIndex)
        }
    }
    fun exception(pos: Int, message: String): Nothing = throw Exception("Parse exception @$pos: $message")
    //returns result and new pos
    fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>?
    fun parse(string: String, flags: F) = parse(string, 0, flags)?.first
    fun completeParse(string: String, flags: F): Result<T>?{
        val (result, pos) = parse(string, 0, flags) ?: return null
        if (pos != string.length) exception(pos, "could not parse past here")
        return result
    }
}
interface ValueParser<T, F>: Parser<T, F>{
    fun internalParse(string: String, pos: Int, flags: F): Pair<T, Int>?
    override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
        val (value, end) = internalParse(string, pos, flags) ?: return null
        return ValueResult<T>(value) to end
    }
}
interface WrappedParser<T, F>: Parser<T, F>{
    val internalParser: Parser<T, F>
    override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
        return internalParser.parse(string, pos, flags)
    }
    companion object{
        operator fun <T, F> invoke(parser: Parser<T, F>) = object: WrappedParser<T, F>{
            override val internalParser = parser
        }
    }
}

class FailProofParser<T, F>(val subParser: Parser<T, F>, val exceptionGenerator: (F, Int)->Exception): Parser<T, F>{
    constructor(subParser: Parser<T, F>, messageGenerator: (F)->String): this(subParser, {flags, pos -> Exception("Parse exception @$pos: ${messageGenerator(flags)}")})
    constructor(subParser: Parser<T, F>, message: String): this(subParser, {_, pos -> Exception("Parse exception @$pos: $message")})

    override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
        return subParser.parse(string, pos, flags) ?: throw exceptionGenerator(flags, pos)
    }
}

class EmptyParser<T, F>: Parser<T, F>{
    override fun parse(string: String, pos: Int, flags: F) = CompoundResult<T>() to pos
}
class NonCapturingParser<T, F>(val subParser: Parser<T, F>): Parser<T, F>{
    override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
        val (results, endPos) = subParser.parse(string, pos, flags) ?: return null
        return results to pos
    }
}
interface CharacterParser<T, F> : Parser<T, F>{
    class Any<T, F>: CharacterParser<T, F>{
        override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
            return if (pos < string.length){
                CharResult<T>(string[pos]) to pos + 1
            }else{
                null
            }
        }
    }
    class Single<T, F>(val char: Char): CharacterParser<T, F>{
        override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
            return if (pos < string.length && string[pos] == char){
                CharResult<T>(char) to pos + 1
            }else{
                null
            }
        }
    }
    class Range<T, F>(val charRange: CharRange): CharacterParser<T, F>{
        override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
            return if (pos < string.length && string[pos] in charRange){
                CharResult<T>(string[pos]) to pos + 1
            }else{
                null
            }
        }
    }
    class CharSet<T, F>(val charSet: Set<Char>): CharacterParser<T, F>{
        override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
            return if (pos < string.length && string[pos] in charSet){
                CharResult<T>(string[pos]) to pos + 1
            }else{
                null
            }
        }
    }
    class Predicated<T, F>(val test: (Char, F)->Boolean): CharacterParser<T, F>{
        override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
            return if (pos < string.length && test(string[pos], flags)){
                CharResult<T>(string[pos]) to pos + 1
            }else{
                null
            }
        }
    }
    companion object{
        operator fun <T, F> invoke(chars: Collection<Char>): CharacterParser<T, F>
                = when(chars.size){
                    1 -> Single(chars.single())
                    else -> CharSet(chars.toSet())
                }

        operator fun <T, F> invoke(vararg chars: Char): CharacterParser<T, F>
                = when (chars.size){
                    0 -> Any()
                    else -> invoke(chars.asList())
                }
        operator fun <T, F> invoke(range: CharRange): CharacterParser<T, F>{
            return Range(range)
        }
        operator fun <T, F> invoke(test: (Char, F)->Boolean): CharacterParser<T, F>{
            return Predicated(test)
        }
    }
}

interface StringParser<T, F>: Parser<T, F>{
    class Matcher<T, F>(val key: String, val ignoreCase: Boolean = false): StringParser<T, F>{
        override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
            return if (string.regionMatches(pos, key, 0, key.length, ignoreCase)){
                StringResult<T>(string.substring(pos, pos + key.length)) to pos + key.length
            }else{
                null
            }
        }
    }
    class Pattern<T, F>(regex: Regex): StringParser<T, F>{
        val regex = regex.pattern.run{
            if (startsWith('^')) regex
            else Regex('^' + regex.pattern)
        }
        override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
            val matchResult = regex.find(Parser.CharSequence(string, pos)) ?: return null
            val range = matchResult.range
            if (range.first != 0) error("Didn't capture at beginning")

            return StringResult<T>(string.substring(pos..pos+range.last)) to range.last + pos + 1
        }
    }
}

class SequentialParser<T, F>(subParsers: List<Parser<T, F>>): Parser<T, F>{
    constructor(vararg subParsers: Parser<T, F>): this(subParsers.toList())
    val subParsers: List<Parser<T, F>>
            = subParsers.map{
        if (it is SequentialParser){
            it.subParsers
        }else{
            listOf(it)
        }
    }.flatten()
    override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
        var endPos = pos
        val results = mutableListOf<Result<T>>()
        for (parser in subParsers){
            val (result, newEnd) = parser.parse(string, endPos, flags) ?: return null
            results.add(result)
            endPos = newEnd
        }
        return CompoundResult(results) to endPos
    }
}

class BranchedParser<T, F>(subParsers: List<Parser<T, F>>): Parser<T, F> {
    constructor(vararg subParsers: Parser<T, F>) : this(subParsers.toList())
    val subParsers: List<Parser<T, F>>
            = subParsers.map {
        if (it is BranchedParser) {
            it.subParsers
        } else {
            listOf(it)
        }
    }.flatten()

    override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
        for (parser in subParsers){
            return parser.parse(string, pos, flags) ?: continue
        }
        return null
    }
}


data class Quantifier(val min: Int, val max: Int){
    init{
        assert(min >= 0)
        assert(max >= min)
    }
}
class QuantifiedParser<T, F>(val subParser: Parser<T, F>, val quantifier: Quantifier): Parser<T, F>{
    override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
        val results = mutableListOf<Result<T>>()
        var count = 0
        var endPos = pos
        while (count < quantifier.max) {
            val (result, newEnd) = subParser.parse(string, endPos, flags) ?: break
            results.add(result)
            endPos = newEnd
            count++
        }
        return if (count in quantifier.min..quantifier.max){
                CompoundResult(results) to endPos
            }else{
                null
            }
    }
}

class LazyParser<T, F>(val initializer: ()->Parser<T, F>): Parser<T, F>{
    private val subParser: Parser<T, F> by lazy(initializer)
    override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
        return subParser.parse(string, pos, flags)
    }
}

class FlagTransformParser<T, Fi, Fo>(val subParser: Parser<T, Fi>, val flagTransform: (Fo)-> Fi): Parser<T, Fo>{
    override fun parse(string: String, pos: Int, flags: Fo): Pair<Result<T>, Int>? {
        return subParser.parse(string, pos, flagTransform(flags))
    }
}

class ResultTransformParser<Ti, To, F>(val subParser: Parser<Ti, F>, val resultTransform: (Result<Ti>, F)->Result<To>): Parser<To, F>{
    override fun parse(string: String, pos: Int, flags: F): Pair<Result<To>, Int>? {
        val (result, endPos) = subParser.parse(string, pos, flags) ?: return null
        return resultTransform(result, flags) to endPos
    }
}

class TransformParser<Ti, Fi, To, Fo>(
    val subParser: Parser<Ti, Fi>,
    val flagTransform: (Fo)->Fi,
    val resultTransform: (Result<Ti>, Fo)->Result<To>
): Parser<To, Fo>{
    override fun parse(string: String, pos: Int, flags: Fo): Pair<Result<To>, Int>? {
        val (result, endPos) = subParser.parse(string, pos, flagTransform(flags)) ?: return null
        return resultTransform(result, flags) to endPos
    }
}

class FixedParser<T, F>(val subParser: Parser<*, F>, val handler: (F)->Result<T>): Parser<T, F>{
    override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
        val (_, endPos) = subParser.parse(string, pos, flags) ?: return null
        return handler(flags) to endPos
    }
}



class BiValue<Ta, Tb> private constructor(private val mode: Modes, private val m_aVal: Ta?, private val m_bVal: Tb?){
    private enum class Modes{A_VAL, B_VAL}

    val aValue: Ta
        get(){
            if (!isAValue()) error("A-value not set")
            return m_aVal as Ta
        }
    val bValue: Tb
        get(){
            if (!isBValue()) error("B-value not set")
            return m_bVal as Tb
        }

    fun isAValue() = mode == Modes.A_VAL
    fun isBValue() = mode == Modes.B_VAL


    companion object{
        fun <Ta, Tb> a(aVal: Ta) = BiValue<Ta, Tb>(Modes.A_VAL, aVal, null)
        fun <Ta, Tb> b(bVal: Tb) = BiValue<Ta, Tb>(Modes.B_VAL, null, bVal)
    }
}
typealias BiParserValue<Ta, Tb> = BiValue<Result<Ta>, Result<Tb>>
typealias BiParser<Ta, Fa, Tb, Fb> = Parser<BiParserValue<Ta, Tb>, Pair<Fa, Fb>>

object BiParserConstructor{
    fun <Ta, Fa, Tb, Fb> a(aParser: Parser<Ta, Fa>): BiParser<Ta, Fa, Tb, Fb>{
        return TransformParser(aParser, {(flags, _) -> flags}){results, _ -> ValueResult(BiValue.a(results))}
    }
    fun <Ta, Fa, Tb, Fb> b(aParser: Parser<Tb, Fb>): BiParser<Ta, Fa, Tb, Fb>{
        return TransformParser(aParser, {(_, flags) -> flags}){results, _ -> ValueResult(BiValue.b(results))}
    }
}