package com.hqurve.parsing.examples

import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileInputStream

internal class JSONParserTest{
    @Test
    fun testNumber(){
        val parser = JSONParser()

        val tests = """
            12.4
            2
            -5
            -5821.42e-1
            "joshua"
            {      }
            [{"jos": 4, "s": [2, 3]}]
        """.trimIndent().split("\n").map{it.trim()}
        for (str in tests){
            try{
                println(str to parser.parseValue(str))
            }catch (e: Exception){
                e.printStackTrace()
            }
        }
    }
    @Test
    fun test(){
        val jsonParser = JSONParser()

        jsonParser.run{
            println(parseObject("""
            {
                "name": "hquvre",
                "friends": ["john", "carl", "carlos"],
                "age": 19,
                "occupation":{
                    "title": "student",
                    "type": "undergrad",
                    "degree": "mathematics",
                    "year": 1
                },
                "is happy": true,
                "gender": "male",
                "message": "(This message is no longer correct and is just used for testing)\n
                I created this general parsing engine (named \"Parser\").\n
                By using this engine, it was very easy for me to create a jsonparser using a simple set of macros.\n
                Moreover, a large portion of the above code is decyphering the encoded numbers and strings which cannot be avoided.\n
                By using this engine, all pattern matching tasks are easily taken care of and allowed the jsonparser to be created quickly.\n
                Of course, a static parser would most likely be quicker than this parser but it is still quite fast. (I still have to do testing though)\n
                But the parser pre-compiles the patterns for each of the macros and reuses matchers allowing it to be quite quick after the first few runs.
                "
            }
        """.replace(Regex("\\s*\\n\\s*"), "")))
            println(parseArray("""
            [
            {"score": 12.5e2, "name":"player1", "max-level": 502}, 5e-3
            ]
        """.replace(Regex("\\s*\\n\\s*"), "")))
        }
    }

    @Test
    fun longTest() {
        val parser = JSONParser()
        val testDirectory = File("../test data/json")

        val listFile = File(testDirectory, "file list.json")

        val fileList = FileInputStream(listFile).run{
            parser.parseArray(String(readAllBytes()))!!.map{it as String}.also{close()}
        }
        println("File list loaded")

        val loadedFiles = fileList.map{fileName ->
            fileName to FileInputStream(File(testDirectory, fileName)).run{String(readAllBytes()).also{close()}}
        }

        println("File contents loaded")

        val loopCount = 1000
        println("Loop count = $loopCount")

        for (pass in 1..3) {
            println()
            println("Starting pass $pass")
            print("Name".padEnd(30))
            print("Total (seconds)".padStart(20))
            print("Average (millis)".padStart(20))
            println()
            for ((fileName, fileContents) in loadedFiles) {
                print("Test $fileName (length=${fileContents.length})".padEnd(30))
                val startTime = System.nanoTime()
                var failed = false
                for (i in 0 until loopCount) {
                    if (parser.parseValue(fileContents) == null) {
                        print("failed on loop $i")
                        failed = true
                        break
                    }
                }
                if (!failed) {
                    val endTime = System.nanoTime()

                    print(String.format("%.6f", (endTime - startTime) / 1_000_000_000.0).padStart(20))
                    print(String.format("%.6f", (endTime - startTime) / (1_000_000.0 * loopCount)).padStart(20))
                }
                println()
            }
            println("Pass $pass complete")
            println()
        }

        println("Test complete")
    }
}