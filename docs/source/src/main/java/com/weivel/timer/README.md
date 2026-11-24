`com.weivel.timer`: the core's next deadline armed on the caller's scheduler, and the one scheduler the layer offers. How the timer follows the core is [the timer module](../../../../../../../modules/timer.md).

<!-- generated: source-children -->
- [CoreTimer.java](../../../../../../../../src/main/java/com/weivel/timer/CoreTimer.java) - arms one core's next deadline on its caller's scheduler
- [NoScheduler.java](../../../../../../../../src/main/java/com/weivel/timer/NoScheduler.java) - `Scheduler.none()`, which arms nothing and reads no clock
- [Scheduler.java](../../../../../../../../src/main/java/com/weivel/timer/Scheduler.java) - the caller's clock and scheduler, and what one must hold to
- [Schedulers.java](../../../../../../../../src/main/java/com/weivel/timer/Schedulers.java) - the schedulers the layer offers its callers
- [SystemScheduler.java](../../../../../../../../src/main/java/com/weivel/timer/SystemScheduler.java) - the scheduler `Schedulers.system()` makes, on one daemon thread reading `System.nanoTime()`
- [TimerObserver.java](../../../../../../../../src/main/java/com/weivel/timer/TimerObserver.java) - hears each move a timer makes: armed for a deadline, or disarmed
- [package-info.java](../../../../../../../../src/main/java/com/weivel/timer/package-info.java) - the package's summary in its Javadoc
<!-- end generated -->
