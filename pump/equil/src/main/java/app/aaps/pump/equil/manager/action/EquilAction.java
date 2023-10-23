package app.aaps.pump.equil.manager.action;


import app.aaps.pump.equil.manager.EquilManager;

public interface EquilAction<T> {
    T execute(EquilManager communicationService);
}
