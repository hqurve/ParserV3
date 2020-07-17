package com.hqurve.parsing.examples

import com.hqurve.parsing.*
import java.lang.StringBuilder
import kotlin.math.pow

class FastJSONParser{
    private fun exception(index: Int, message: String): Nothing = throw Exception("Parse exception @$index: $message")
    private fun parseNumber(string: String, pos: Int): Pair<Number, Int>?{
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

    private fun parseString(string: String, pos: Int): Pair<String, Int>? {
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

    private fun parseKVPair(string: String, pos: Int): Pair<Pair<String, Any?>, Int>? {
        val (key, end1) = parseString(string, pos) ?: return null

        var index = end1
        while (index != string.length && string[index] in " \t\r\n") index++
        if (index == string.length || string[index] != ':') exception(index, "expected colon")
        index++
        while (index != string.length && string[index] in " \t\r\n") index++

        val (value, end2) = parseValue(string, index)

        return (key to value) to end2
    }

    private fun parseObject(string: String, pos: Int): Pair<Map<String, Any?>, Int>? {
        if (pos == string.length || string[pos] != '{') return null

        var index = pos + 1
        while (index != string.length && string[index].isWhitespace()) index++

        if (index == string.length) exception(index, "unexpected end of object")

        if (string[index] == '}') return Pair(emptyMap(), index + 1)

        val kvPairList = mutableListOf<Pair<String, Any?>>()

        parseKVPair(string, index)?.also{
            val (pair, end) = it
            kvPairList.add(pair)
            index = end
        }?: exception(index, "expected kvPair")

        while (index != string.length && string[index].isWhitespace()) index++

        while(index < string.length && string[index] == ','){
            index++
            while (index != string.length && string[index].isWhitespace()) index++

            val (pair, end) = parseKVPair(string, index)?: exception(index, "expected kvPair")
            kvPairList.add(pair)
            index = end

            while (index != string.length && string[index].isWhitespace()) index++
        }

        if (index == string.length) exception(index, "unexpected end of object")
        if (string[index] != '}') exception(index, "object missing end brace")
        return kvPairList.toMap() to index + 1
    }


    private fun parseArray(string: String, pos: Int): Pair<List<Any?>, Int>? {
        if (pos == string.length || string[pos] != '[') return null

        var index = pos + 1
        while (index != string.length && string[index].isWhitespace()) index++

        if (index == string.length) exception(index, "unexpected end of array")

        if (string[index] == ']') return Pair(emptyList(), index + 1)

        val itemList = mutableListOf<Any?>()

        parseValue(string, index).also{
            val (item, end) = it
            itemList.add(item)
            index = end
        }

        while (index != string.length && string[index].isWhitespace()) index++

        while(index < string.length && string[index] == ','){
            index++
            while (index != string.length && string[index].isWhitespace()) index++

            val (item, end) = parseValue(string, index)
            itemList.add(item)
            index = end

            while (index != string.length && string[index].isWhitespace()) index++
        }

        if (index == string.length) exception(index, "unexpected end of array")
        if (string[index] != ']') exception(index, "object missing end brace")
        return itemList to index + 1
    }

    private fun parseValue(string: String, pos: Int): Pair<Any?, Int>{
        var index = pos
        while (index != string.length && string[index].isWhitespace()) index++
        val (value, end) = when{
            string.regionMatches(index, "null", 0, 4) -> null to index + 4
            string.regionMatches(index, "true", 0, 4) -> true to index + 4
            string.regionMatches(index, "false", 0, 5) -> false to index + 5
            else -> parseObject(string, index) ?: parseArray(string, index) ?: parseNumber(string, index) ?: parseString(string, index)
        }?: exception(index, "expected value")
        index = end
        while (index != string.length && string[index].isWhitespace()) index++
        return value to end
    }

    fun parseObject(string: String): Map<String, Any?>?{
        var index = 0
        while (index != string.length && string[index].isWhitespace()) index++
        return parseObject(string, index)?.first
    }
    fun parseArray(string: String): List<Any?>?{
        var index = 0
        while (index != string.length && string[index].isWhitespace()) index++
        return parseArray(string, index)?.first
    }
    fun parseValue(jsonString: String) = parseValue(jsonString, 0).first
}