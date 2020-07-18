package com.hqurve.parsing.examples

import com.hqurve.parsing.*
import java.lang.StringBuilder
import kotlin.math.pow

/*
    Example of json parser using specification from https://tools.ietf.org/html/rfc4627
    (and http://www.ecma-international.org/publications/files/ECMA-ST/ECMA-404.pdf)
 */


class JSONParser{
    private open class ParserGenerator<T, F>: com.hqurve.parsing.ParserGenerator<T, F>(){
        val wSpace = object: Parser<T, F> {
            override fun parse(string: String, pos: Int, flags: F): Pair<Result<T>, Int>? {
                var index = pos
                while (index < string.length && string[index].isWhitespace()) {
                    index++
                }
                return CompoundResult<T>() to index
            }
        }
    }
    private object Assist: ParserGenerator<Unit, Unit>()

    private fun <T, F> builder(block: ParserGenerator<T, F>.()->Parser<T, F>): Parser<T, F>{
        return ParserGenerator<T, F>().run(block)
    }

    private class BiParserWrapper<Ta, Fa, Tb, Fb>: ParserGenerator<BiParserValue<Ta, Tb>, Pair<Fa, Fb>>(){
        fun a(aParser: Parser<Ta, Fa>) = BiParserConstructor.a<Ta, Fa, Tb, Fb>(aParser)
        fun b(bParser: Parser<Tb, Fb>) = BiParserConstructor.b<Ta, Fa, Tb, Fb>(bParser)
    }

    private fun <Ta, Fa, Tb, Fb> biParserBuilder(block: BiParserWrapper<Ta, Fa, Tb, Fb>.()->BiParser<Ta, Fa, Tb, Fb>): BiParser<Ta, Fa, Tb, Fb>{
        return BiParserWrapper<Ta, Fa, Tb, Fb>().run(block)
    }


    private lateinit var valueParser: Parser<Any?, Unit>

    private val numberParser = object: ValueParser<Number, Unit>{
        override fun internalParse(string: String, pos: Int, flags: Unit): Pair<Number, Int>? {
            if (pos == string.length || !(string[pos] == '-' || string[pos].isDigit())) return null

            var index = pos

            if (string[index] == '-'){
                index++
            }
            while(index < string.length && string[index].isDigit()){
                index++
            }

            if (index == string.length || string[index] !in "eE.") return string.substring(pos, index).toLong() to index

            if (string[index] == '.'){
                index++
                if (index == string.length || !string[index].isDigit()) exception(index, "expected decimal part")
                while(index < string.length && string[index].isDigit()){
                    index++
                }

                if (index == string.length || string[index] !in "eE") return string.substring(pos, index).toDouble() to index
            }

            val mantissaEnd = index

            index++
            if (index == string.length) exception(index, "expected exponent")
            val exponentStart = index
            if (string[index] in "-+"){
                index++
            }
            if (index == string.length || !string[index].isDigit()) exception(index - 1, "invalid exponent")

            while (index < string.length && string[index].isDigit()){
                index++
            }
            return string.substring(pos, mantissaEnd).toDouble() * 10.0.pow(string.substring(exponentStart, index).toInt()) to index
        }
    }


    private val stringParser = object: ValueParser<String, Unit>{
        override fun internalParse(string: String, pos: Int, flags: Unit): Pair<String, Int>? {
            if (pos == string.length || string[pos] != '"') return null

            val sb = StringBuilder()
            var index = pos + 1
            while(index < string.length && string[index] != '"'){
                if (string[index] in '\u0000'..'\u001F') exception(index, "encountered unescaped control code")
                if (string[index] == '\\'){
                    if (index + 1 == string.length) exception(index + 1, "expected escaped character")
                    sb.append(when(string[index + 1]){
                        '\"' -> '\"'
                        '\\' -> '\\'
                        '/'  -> '/'
                        'b' -> '\u0008'
                        'f' -> '\u000C'
                        'n' -> '\u000A'
                        'r' -> '\u000D'
                        't' -> '\u0009'
                        'u' ->{
                            if (index + 6 > string.length) throw CharacterCodingException()
                            val codePoint = string.substring(index + 2, index + 6).toInt(16)
                            Character.toChars(codePoint)[0]
                            index += 4
                        }
                        else -> exception(index + 1, "invalid escape code")
                    })
                    index += 2
                }else{
                    sb.append(string[index])
                    index++
                }
            }
            if (index == string.length) exception(index, "unexpected end of string")
            if (string[index] != '"') exception(index, "expected end quotation")
            return sb.toString() to index+1
        }
    }

    private val kvPairParser = object: ValueParser<Pair<String, Any?>, Unit>{
        override fun internalParse(string: String, pos: Int, flags: Unit): Pair<Pair<String, Any?>, Int>? {
            val (key, end1) = stringParser.parse(string, pos, flags) ?: return null
            var index = end1
            while (index < string.length && string[index].isWhitespace()) index++

            if (index == string.length || string[index] != ':') exception(index, "expected ':'")
            index++

            while (index < string.length && string[index].isWhitespace()) index++

            val (value, end2) = valueParser.parse(string, index, flags) ?: exception(index, "expected value")

            return Pair(key.asValue(), value.asValue()) to end2
        }
    }
//    init{
//        kvPairParser = biParserBuilder<String, Unit, Any?, Unit> {
//            lz{ a(stringParser) .. wSpace .. exact(':') .. wSpace .. b(valueParser)}
//        }.transValue({Unit to Unit}){results, _ ->
//            results as CompoundResult
//            results.valueAt(0).aValue.asValue() to results.valueAt(4).bValue.asValue()
//        }
//    }

    private val objectParser: Parser<Map<String, Any?>, Unit>
    init{
        objectParser = Assist.exact('{')..Assist.wSpace ignoreFirst BranchedParser(
            builder<Pair<String, Any?>, Unit> {
                kvPairParser .. wSpace .. (exact(',') .. wSpace .. kvPairParser .. wSpace) * q(0)
            } transResultValue {results, _->
                results as CompoundResult
                val primary = results.valueAt(0)
                val trailingResult = results.compoundAt(2).map{it.asCompound().valueAt(2)}
                (listOf(primary) + trailingResult).toMap()
            },
            builder{empty() fixedResultValue { emptyMap()} }
        ) ignoreNext Assist.exact('}').attachError("Object missing end brace")
    }

    private val arrayParser: Parser<List<Any?>, Unit>
    init{
        arrayParser = Assist.exact('[')..Assist.wSpace ignoreFirst BranchedParser(
            builder<Any?, Unit> {
                lz{ valueParser .. wSpace .. (exact(',') .. wSpace .. valueParser .. wSpace) * q(0) }
            } transResultValue {results, _->
                results as CompoundResult
                val primary = results.valueAt(0)
                val trailingResult = results.compoundAt(2).map{it.asCompound().valueAt(2)}
                listOf(primary) + trailingResult
            },
            builder{empty() fixedResultValue { emptyList() } }
        ) ignoreNext Assist.exact(']').attachError("Array missing end brace")
    }


    init{
        valueParser = Assist.wSpace ignoreFirst BranchedParser(
            Assist.exact("null") fixedResultValue { null },
            Assist.exact("true") fixedResultValue { true },
            Assist.exact("false") fixedResultValue { false },
            objectParser,
            arrayParser,
            numberParser,
            stringParser
        ).attachError("Unknown value") ignoreNext Assist.wSpace
    }

    fun parseObject(jsonString: String) = objectParser.completeParse(jsonString, Unit)?.asValue()
    fun parseArray(jsonString: String) = arrayParser.completeParse(jsonString, Unit)?.asValue()
    fun parseValue(jsonString: String) = valueParser.completeParse(jsonString, Unit)?.asValue()
}