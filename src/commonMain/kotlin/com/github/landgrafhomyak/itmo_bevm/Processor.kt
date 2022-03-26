package com.github.landgrafhomyak.itmo_bevm

import kotlin.jvm.JvmField

@Suppress("unused")
class Processor<T : AbstractCommand>(
    @Suppress("MemberVisibilityCanBePrivate")
    val commands: AbstractCommandRegistry<T>
) {
    class Registers internal constructor() {
        @JvmField
        var accumulator: BevmByte = BevmByte.uninitialized()

        @JvmField
        internal var data: BevmByte = BevmByte.uninitialized()

        @JvmField
        internal var buffer: BevmByte = BevmByte.uninitialized()

        @JvmField
        internal var command: BevmByte = BevmByte.uninitialized()

        @JvmField
        internal var programState: BevmByte = BevmByte.uninitialized()

        @JvmField
        var stackPointer: BevmByte = BevmByte.uninitialized()

        @JvmField
        internal var input: BevmByte = BevmByte.uninitialized()

        @JvmField
        var instructionPointer: BevmByte = BevmByte.uninitialized()

        @JvmField
        var address: BevmByte = BevmByte.uninitialized()
    }

    @JvmField
    val registers = Registers()

    @Suppress("MemberVisibilityCanBePrivate")
    inner class Flags internal constructor() {
        var carry by this@Processor.registers.programState.bitProperty(0u)

        @Suppress("PropertyName")
        @Deprecated("Используйте полное имя флага", ReplaceWith("carry"))
        val C by ::carry

        var overflow by this@Processor.registers.programState.bitProperty(1u)

        @Suppress("PropertyName")
        @Deprecated("Используйте полное имя флага", ReplaceWith("overflow"))
        val V by ::overflow

        var zero by this@Processor.registers.programState.bitProperty(2u)

        @Suppress("PropertyName")
        @Deprecated("Используйте полное имя флага", ReplaceWith("zero"))
        val Z by ::zero

        var sign by this@Processor.registers.programState.bitProperty(3u)

        @Suppress("PropertyName")
        @Deprecated("Используйте полное имя флага", ReplaceWith("sign"))
        val N by ::sign

        var allowInterruption by this@Processor.registers.programState.bitProperty(5u)

        @Suppress("PropertyName")
        @Deprecated("Используйте полное имя флага", ReplaceWith("allowInterruption"))
        val EI by ::allowInterruption

        var interruption by this@Processor.registers.programState.bitProperty(6u)

        @Suppress("PropertyName")
        @Deprecated("Используйте полное имя флага", ReplaceWith("interruption"))
        val INT by ::interruption

        var running by this@Processor.registers.programState.bitProperty(7u)

        @Suppress("PropertyName")
        @Deprecated("Используйте полное имя флага", ReplaceWith("running"))
        val W by ::running

        @Suppress("PropertyName")
        val P by this@Processor.registers.programState.bitProperty(8u)

        @Suppress("CanBePrivate")
        fun recalcFrom(byte: BevmByte) {
            this.zero = byte.isZero()
            this.sign = byte.signBit
        }

        fun recalcFromAccumulator() = this.recalcFrom(this@Processor.registers.accumulator)
    }

    @JvmField
    val flags = this.Flags()


    @Suppress("MemberVisibilityCanBePrivate", "CanBeParameter")
    inner class Memory internal constructor(val size: UInt) {
        private val data = Array(this.size.toInt()) { BevmByte.uninitialized() }

        operator fun get(address: UInt): BevmByte = this@Memory.data[address.toInt()]

        operator fun get(address: BevmByte): BevmByte {
            this@Processor.registers.address = address
            return this@Memory[address.toUnsigned()].also { v -> this@Processor.registers.data = v }
        }

        operator fun set(address: UInt, value: BevmByte) {
            this@Memory[address] = value
        }

        operator fun set(address: UInt, value: UInt) {
            this@Memory[address] = BevmByte.fromUnsigned(value)
        }

        operator fun set(address: BevmByte, value: BevmByte) {
            this@Processor.registers.address = address
            this@Processor.registers.data = value
            this@Memory[address.toUnsigned()] = value
        }

        fun dump(): Array<UByte> = (0u until size).map(this::get).map(BevmByte::toUnsigned).flatMap { u -> listOf((u shr 8) and 0xffu, u and 0xffu) }.map(UInt::toUByte).toTypedArray()
    }

    @JvmField
    val memory = Memory(2048u)

    /**
     * Запускает программу в заданном адресе
     */
    fun runAt(address: UInt) {
        this.flags.running = true
        this.registers.instructionPointer = BevmByte.fromUnsigned(address)
        @Suppress("MemberVisibilityCanBePrivate")
        while (true) {
            this.registers.buffer = this.registers.instructionPointer
            this.registers.command = this.memory[this.registers.instructionPointer++]
            val command = this.commands.parse(this.registers.command)
            try {
                command.execute(this)
            } catch (_: ShutdownSignal) {
                break
            }
        }
        this.flags.running = false
    }
}