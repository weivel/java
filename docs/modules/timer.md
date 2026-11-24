# `com.weivel.timer`

Arms the core's next deadline on this language's timer, as the core's `docs/contract/timer.md` asks of a language's
`timer` module: the core owns no clock and takes time only as a number its caller hands in. Its classes are listed in
[its source doc](../source/src/main/java/com/weivel/timer/README.md).

`CoreTimer` arms the core's next deadline on the caller's `Scheduler`, which owns the clock: it follows the latest
deadline announcement drained and nothing else, hands the core the time through the wrapper when its task runs, waits
in steps on a scheduler that cannot wait as long, and gives the wrapper the clock for its check before each call.
One timer follows one core: a second started while one follows is the caller's mistake, and a timer's destroy takes
off its own hooks and nothing else.
`Schedulers.system()` makes a scheduler of its own on one daemon thread reading `System.nanoTime()`, owned and closed
by whoever made it, its thread running the listeners of the core it fires for. `CoreTimer.start` refuses a scheduler
that can wait no time. `Scheduler.none()` arms nothing and reads no clock.
