package com.dbbackup.domain.port;

import com.dbbackup.domain.model.StateRecord;

public interface StateTracker {
    StateRecord getState(String dbName);
    void saveState(String dbName, StateRecord state);
}
