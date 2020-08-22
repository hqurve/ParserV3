package com.hqurve.parsing

open class ParserGenerator<T, F>{
    fun empty() = EmptyParser<T, F>()

    fun exact(vararg chars: Char) = CharacterParser<T, F>(chars.toSet())
    fun exact(chars: Collection<Char>) = CharacterParser<T, F>(chars)
    fun exact(charRange: CharRange) = CharacterParser<T, F>(charRange)

    fun exact(test: (Char, F)-> Boolean) = CharacterParser<T, F>(test)
    fun exact(test: (Char)->Boolean) = CharacterParser<T, F>{char, _ -> test(char)}


    fun exact(string: String, ignoreCase: Boolean = false) = StringParser.Matcher<T, F>(string, ignoreCase)
}

class BiParserWrapper<Ta, Fa, Tb, Fb>: ParserGenerator<BiParserValue<Ta, Tb>, Pair<Fa, Fb>>(){
    fun a(aParser: Parser<Ta, Fa>) = BiParserConstructor.a<Ta, Fa, Tb, Fb>(aParser)
    fun b(bParser: Parser<Tb, Fb>) = BiParserConstructor.b<Ta, Fa, Tb, Fb>(bParser)
}


object Assist: ParserGenerator<Unit, Unit>()
fun <T, F> builder(block: ParserGenerator<T, F>.()->Parser<T, F>): Parser<T, F>{
    return ParserGenerator<T, F>().run(block)
}

fun <Ta, Fa, Tb, Fb> biParserBuilder(block: BiParserWrapper<Ta, Fa, Tb, Fb>.()->BiParser<Ta, Fa, Tb, Fb>): BiParser<Ta, Fa, Tb, Fb>{
    return BiParserWrapper<Ta, Fa, Tb, Fb>().run(block)
}


operator fun <T, F> Parser<T, F>.rangeTo(other: Parser<T, F>) = SequentialParser(this, other)

infix fun <T, F> Parser<T, F>.or(other: Parser<T, F>) = BranchedParser(this, other)


operator fun <T, F> Parser<T, F>.times(quantifier: Quantifier)
        = QuantifiedParser(this, quantifier)
operator fun <T, F> Parser<T, F>.times(amt: Int) = this * q(amt, amt)

fun q(min: Int, max: Int = Int.MAX_VALUE) = Quantifier(min, max)
val maybe = q(0, 1)

fun <T, F> lz(initializer: ()->Parser<T, F>) = LazyParser(initializer)


fun <T, F> Parser<T, F>.asNonCapture() = NonCapturingParser(this)
fun <T, F> Parser<T, F>.capture() = WrappedParser(this)

infix fun <T, F> Parser<*, F>.ignoreFirst(other: Parser<T, F>): Parser<T, F>{
    val self = this
    return object: Parser<T, F>{
        override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
            val (_, end) = self.parse(string, pos, flags) ?: return null
            return other.parse(string, end, flags)
        }
    }
}

infix fun <T, F> Parser<T, F>.ignoreNext(other: Parser<*, F>): Parser<T, F>{
    val self = this
    return object: Parser<T, F>{
        override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
            val (result, end) = self.parse(string, pos, flags) ?: return null
            return other.parse(string, end, flags)?.second?.let{ result to it }
        }
    }
}

fun <T, F> Parser<T, F>.attachError(exceptionGenerator: (F, Int)->Exception) = FailProofParser(this, exceptionGenerator)
fun <T, F> Parser<T, F>.attachError(messageGenerator: (F)->String) = FailProofParser(this, messageGenerator)
fun <T, F> Parser<T, F>.attachError(message: String) = FailProofParser(this, message)


fun <T, F> Parser<T, F>.assert(checker: (Result<T>, F)->String?) = VerifyingParser(this, checker)
fun <T, F> Parser<T, F>.assert(checker: (Result<T>)->String?) = VerifyingParser(this){results, _ -> checker(results)}


fun <Ti, Fi, To, Fo> Parser<Ti, Fi>.trans(flagsTransform: (Fo)->Fi, resultTransform: (Result<Ti>, Fo)->Result<To>)
        = TransformParser(this, flagsTransform, resultTransform)
fun <Ti, Fi, To, Fo> Parser<Ti, Fi>.transValue(flagsTransform: (Fo)->Fi, resultTransform: (Result<Ti>, Fo)->To)
        = TransformParser(this, flagsTransform){results, flags -> ValueResult(resultTransform(results, flags))}

infix fun <Ti, To, F> Parser<Ti, F>.transResult(handler: (Result<Ti>, F)->Result<To>)
        = ResultTransformParser(this, handler)
infix fun <Ti, To, F> Parser<Ti, F>.transResultValue(handler: (Result<Ti>, F)->To)
        = ResultTransformParser(this){ results, flags -> ValueResult(handler(results, flags)) }

infix fun <T, F> Parser<T, F>.transResultChar(handler: (Result<T>, F)-> Char)
        = ResultTransformParser(this){ results, flags -> CharResult<T>(handler(results, flags)) }
infix fun <T, F> Parser<T, F>.transResultString(handler: (Result<T>, F)-> String)
        = ResultTransformParser(this){ results, flags -> StringResult<T>(handler(results, flags)) }

infix fun <T, F> Parser<*, F>.fixedResult(handler: (F)->Result<T>) = FixedParser(this, handler)
infix fun <T, F> Parser<*, F>.fixedResultValue(handler: (F)->T)
        = FixedParser(this){flags -> ValueResult(handler(flags))}
infix fun <T, F> Parser<*, F>.fixedResultValue(value: T)
        = FixedParser(this){ValueResult(value)}
infix fun <T, F> Parser<*, F>.fixedResultChar(handler: (F)->Char)
        = FixedParser(this){flags -> CharResult<T>(handler(flags))}
infix fun <T, F> Parser<*, F>.fixedResultString(handler: (F)->String)
        = FixedParser(this){flags -> StringResult<T>(handler(flags))}

infix fun <T, Fi, Fo> Parser<T, Fi>.transFlags(handler: (Fo)->Fi)
        = FlagTransformParser(this, handler)



