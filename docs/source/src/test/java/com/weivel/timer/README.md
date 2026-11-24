The tests of `com.weivel.timer`, and the clock and scheduler other packages' tests own. What they hold the timer to is [the timer module](../../../../../../../modules/timer.md).

<!-- generated: source-children -->
- [SystemSchedulerTest.java](../../../../../../../../src/test/java/com/weivel/timer/SystemSchedulerTest.java) - `Schedulers.system()` on its real executor
- [TestScheduler.java](../../../../../../../../src/test/java/com/weivel/timer/TestScheduler.java) - a clock and scheduler a test owns, counting every clock read
- [TimerTest.java](../../../../../../../../src/test/java/com/weivel/timer/TimerTest.java) - the timer, each rule of the contract's `timer` section
- [TimerThreadsTest.java](../../../../../../../../src/test/java/com/weivel/timer/TimerThreadsTest.java) - the timer under several threads, each interleaving forced by gates
<!-- end generated -->
