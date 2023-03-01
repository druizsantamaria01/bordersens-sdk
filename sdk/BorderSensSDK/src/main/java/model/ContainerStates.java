package model;

public enum ContainerStates {
    RUNNING,CREATED,EXITED,PAUSED,RESTARTING,ALL;

    public static ContainerStates getState(String state) {
        switch(state.toUpperCase()) {
            case "RUNNING":
                return ContainerStates.RUNNING;

            case "CREATED":
                return ContainerStates.CREATED;

            case "EXITED":
                return ContainerStates.EXITED;

            case "PAUSED":
                return ContainerStates.PAUSED;

            case "RESTARTING":
                return ContainerStates.RESTARTING;

            case "ALL":
                return ContainerStates.ALL;

            default:
                return null;
        }
    }
}
