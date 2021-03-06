@file:Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
@file:OptIn(ExperimentalUnsignedTypes::class)

package com.github.landgrafhomyak.itmo_bevm.cli

@Suppress("MemberVisibilityCanBePrivate", "unused")
enum class Commands(
    val alias: String,
    val help: String,
    val options: Map<String, Option>,
    val action: (Map<String, Any?>) -> Int
) {
    Help(
        "help", "показывает это сообщение",
        mapOf(),
        action@{
            println("Использование:")
            println("\tbevm <команда>")
            println()
            println("Команды:")

            val maxRowSize: UInt
            Commands.byAlias.map { (name, command) ->
                "$name " + command.options.values.joinToString(separator = " ") { option ->
                    var s = option.aliases.joinToString(separator = " | ")
                    if (option.aliases.size > 1 && (option.required || option.type != null)) {
                        s = "( $s )"
                    }
                    if (option.type != null) {
                        "<${option.type.label}>".also { a ->
                            s = if (option.aliases.isEmpty()) a else "$s $a"
                        }
                    }
                    if (!option.required) {
                        s = "[ $s ]"
                    }
                    return@joinToString s
                } to command.help
            }.apply {
                maxRowSize = maxOf { (usage, _) -> usage.length }.toUInt()
            }.forEach { (usage, help) ->
                println("\t" + usage.padEnd(maxRowSize.toInt(), ' ') + " - $help")
            }

            println()
            println("Исходный код: https://github.com/landgrafhomyak/itmo-bevm")
            return@action 0
        }
    ),
    Run("run", "запускает скомпилированную программу",
        mapOf(
            "out" to Option(Option.OptionType.BinFile, false, "--dump"),
            "ip" to Option(Option.OptionType.Unsigned, false, "-ip"),
            "len" to Option(Option.OptionType.Unsigned, false, "-l", "--length"),
            "in" to Option(Option.OptionType.BinFile, true)
        ),
        action@{ args ->
            val `in`: FileLike.Binary = args["in"]!! as FileLike.Binary
            val out: FileLike.Binary? = args["out"] as FileLike.Binary?
            val len: UInt = (args["len"] ?: 16u) as UInt
            val start: UInt = (args["ip"] ?: 0u) as UInt

            val image = `in`.readAll()
            val proc = Processor(DefaultCommandRegistry)

            if (start >= proc.memory.size) {
                eprintln("Невалидный стартовый адрес")
                return@action -1
            }

            if (image.size.toUInt() > proc.memory.size * 2u) {
                eprintln("Образ слишком большой")
                return@action -1
            }

            proc.memory.load((image + Array(proc.memory.size.toInt() * 2 - image.size) { (0).toUByte() }))

            proc.runAt(start)

            val dump = proc.memory.dump()

            viewBin(TextStd, dump, len, true)

            out?.write(dump)

            return@action 0
        }
    ),


    PrettyBin(@Suppress("SpellCheckingInspection") "viewbin", "форматирует бинарные файлы в текстовый вид",
        mapOf(
            "out" to Option(Option.OptionType.TextFile, false, "-o", "--out"),
            "len" to Option(Option.OptionType.Unsigned, false, "-l", "--length"),
            "word" to Option(null, false, "-w", "--word"),
            "in" to Option(Option.OptionType.BinFile, true)
        ),
        action@{ args ->
            val `in`: FileLike.Binary = args["in"]!! as FileLike.Binary
            val out: FileLike.Text? = args["out"] as FileLike.Text?
            val len: UInt = (args["len"] ?: 16u) as UInt
            val word: Boolean = args.containsKey("word")

            if (len == 0u) {
                eprintln("Длинна строки должна быть больше нуля")
                return@action -1
            }

            val bin = `in`.readAll()
            viewBin(TextStd, bin, len, word)
            if (out != null)
                viewBin(out, bin, len, word)
            return@action 0
        }
    ),
    Trace("trace", "выполняет трассировку скомпилированной программы",
        mapOf(
            "out" to Option(Option.OptionType.TextFile, false, "-o", "--out"),
            "ip" to Option(Option.OptionType.Unsigned, false, "-ip"),
            "in" to Option(Option.OptionType.BinFile, true)
        ),
        action@{ args ->
            val `in`: FileLike.Binary = args["in"]!! as FileLike.Binary
            val out: FileLike.Text? = args["out"] as FileLike.Text?
            val start: UInt = (args["ip"] ?: 0u) as UInt

            val image = `in`.readAll()
            val proc = Processor(DefaultCommandRegistry)

            if (start >= proc.memory.size) {
                eprintln("Невалидный стартовый адрес")
                return@action -1
            }

            if (image.size.toUInt() > proc.memory.size * 2u) {
                eprintln("Образ слишком большой")
                return@action -1
            }

            proc.memory.load((image + Array(proc.memory.size.toInt() * 2 - image.size) { (0).toUByte() }))

            @Suppress("SpellCheckingInspection")
            val h = " addr   cmd  |  AC   DR   BR   CR   PS   IP   IR   SP   AR  C V Z N | \n"
            print(h)

            out?.write(h)
            proc.trace(start) { ip, cr, dec ->
                val s = "0x${ip.toString(16).padStart(3, '0')} - ${cr.toString(16).padStart(4, '0')} | " +
                        "${registers.accumulator.formatToString()} ${registers.data.formatToString()} " +
                        "${registers.buffer.formatToString()} ${registers.command.formatToString()} " +
                        "${registers.programState.formatToString()}  ${registers.instructionPointer.formatToStringP()} " +
                        "${registers.input.formatToString()}  ${registers.stackPointer.formatToStringP()} " +
                        " ${registers.address.formatToStringP()} " +
                        "${if (flags.carry) '+' else '0'} " +
                        "${if (flags.overflow) '+' else '0'} " +
                        "${if (flags.zero) '+' else '0'} " +
                        "${if (flags.sign) '+' else '0'} " +
                        "| $dec\n"
                print(s)
                out?.write(s)
            }

            return@action 0
        }
    ),
    ;

    init {
        @Suppress("RemoveRedundantQualifierName")
        Commands.byAlias[this.alias] = this
    }

    fun execute(args: Map<String, Any?>): Int = this.action(args)

    companion object {
        private val byAlias = mutableMapOf<String, Commands>()
        fun byAlias(name: String): Commands? = this.byAlias[name]

        init {
            @Suppress("SpellCheckingInspection")
            /* Костыль, чтобы починить ленивую инициализацию членов енама в Kotlin/Native */
            @Suppress("ControlFlowWithEmptyBody", "RemoveRedundantQualifierName")
            for (v in Commands.values()) {
            }
        }
    }


}