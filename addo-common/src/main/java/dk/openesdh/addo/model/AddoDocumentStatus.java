package dk.openesdh.addo.model;

public enum AddoDocumentStatus {
    STARTED(2),
    COMPLETED(3);

    public final int stateId;

    private AddoDocumentStatus(int stateId) {
        this.stateId = stateId;
    }

    public static AddoDocumentStatus of(int stateId) {
        for (AddoDocumentStatus state : values()) {
            if (state.stateId == stateId) {
                return state;
            }
        }
        return null;
    }
}
