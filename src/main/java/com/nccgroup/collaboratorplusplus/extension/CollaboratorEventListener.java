package com.nccgroup.collaboratorplusplus.extension;

import com.google.gson.JsonArray;

public interface CollaboratorEventListener {
    void onPollingRequestSent(String biid, boolean isFirstPoll);
    void onPollingResponseRecieved(String biid, JsonArray interactions);
}
