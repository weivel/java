The tests of `com.weivel.benchmarks`: the gates themselves, run only by the `benchmark` task, and the judging, sampling and allocation tests the suite runs. What the gates time is [the benchmarks module](../../../../../../../modules/benchmarks.md).

<!-- generated: source-children -->
- [BareServiceGetTest.java](../../../../../../../../src/test/java/com/weivel/benchmarks/BareServiceGetTest.java) - the bare call the control ratio weighs the wrapper against, on the real core
- [BenchmarksTest.java](../../../../../../../../src/test/java/com/weivel/benchmarks/BenchmarksTest.java) - the benchmark gates on the real core, run by the `benchmark` task alone
- [ControlCallAllocationTest.java](../../../../../../../../src/test/java/com/weivel/benchmarks/ControlCallAllocationTest.java) - what a warm `weivel_get_service` through the wrapper allocates on the heap
- [DescribedCallBenchmarkTest.java](../../../../../../../../src/test/java/com/weivel/benchmarks/DescribedCallBenchmarkTest.java) - the described call's paths outside the timed gate, each answering its sample
- [GatesTest.java](../../../../../../../../src/test/java/com/weivel/benchmarks/GatesTest.java) - the gates judged on given bounds and samples
- [LanguageBudgetTest.java](../../../../../../../../src/test/java/com/weivel/benchmarks/LanguageBudgetTest.java) - `benchmarks/java.txt` read with a gate per backend
- [MeasuringTest.java](../../../../../../../../src/test/java/com/weivel/benchmarks/MeasuringTest.java) - how paths are sampled side by side and summarised
- [SameLanguageGateTest.java](../../../../../../../../src/test/java/com/weivel/benchmarks/SameLanguageGateTest.java) - the same-language gate's refusal
<!-- end generated -->
