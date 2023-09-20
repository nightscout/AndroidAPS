package com.microtechmd.equil.manager.action;


import com.microtechmd.equil.manager.EquilManager;

public interface EquilAction<T> {
    T execute(EquilManager communicationService);
}
