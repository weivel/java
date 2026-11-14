package com.weivel.host;

/** Where one plugin's activation ended. */
public enum ActivationState {
    /** Its activate completed and the core verified it. */
    ACTIVE,
    /** Its activate failed, or the core refused its verification, and the host quarantined it. */
    QUARANTINED,
    /** The core refused to open it. */
    REFUSED,
    /** It was released while its activate was in flight, or the destroy kept it from its activate. */
    RELEASED,
    /** Its open failed for a reason other than the core's refusal, a defect of the layer told to the error sink. */
    FAILED
}
