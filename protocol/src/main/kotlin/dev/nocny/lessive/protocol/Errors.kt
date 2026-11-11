package dev.nocny.lessive.protocol

import java.io.IOException

/** Failures the UI can explain. Transport loss surfaces as [IOException]. */
sealed class WasherException(message: String) : Exception(message) {

    /** The tag answered with something other than 90 00 at [step]. */
    class UnexpectedStatus(val step: String, val sw: Int) :
        WasherException("$step: SW %04X".format(sw))

    /** The write password was refused. [retriesLeft] comes from 63 Cx when known. */
    class Locked(val retriesLeft: Int?) : WasherException("write password refused")

    /** The machine refused the parameters (6A 80 / 6B 00 and friends). */
    class Refused(val sw: Int) : WasherException("command refused: SW %04X".format(sw))

    /** The command was written and signalled, but the machine never acknowledged it. */
    class NoAcknowledgement : WasherException("no acknowledgement from the machine")

    /** The phone moved away after the command was committed: the machine may have it. */
    class LostAfterCommit(cause: IOException) :
        WasherException("connection lost after the command was written") {
        init { initCause(cause) }
    }
}
