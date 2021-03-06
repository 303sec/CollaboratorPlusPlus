package com.nccgroup.collaboratorplusplus.extension;

import burp.IBurpExtenderCallbacks;
import com.coreyd97.BurpExtenderUtilities.IGsonProvider;
import com.coreyd97.BurpExtenderUtilities.PreferenceFactory;
import com.coreyd97.BurpExtenderUtilities.Preferences;
import com.google.gson.reflect.TypeToken;
import com.nccgroup.collaboratorplusplus.extension.context.*;
import com.nccgroup.collaboratorplusplus.utilities.LevelSerializer;
import org.apache.logging.log4j.Level;

import java.util.ArrayList;

import static com.nccgroup.collaboratorplusplus.extension.Globals.*;

public class CollaboratorPreferenceFactory extends PreferenceFactory {

    public CollaboratorPreferenceFactory(IGsonProvider gsonProvider, IBurpExtenderCallbacks callbacks) {
        super(EXTENSION_NAME, gsonProvider, callbacks);
    }

    @Override
    protected void createDefaults() {

    }

    @Override
    protected void registerTypeAdapters() {
        gsonProvider.registerTypeAdapter(Interaction.class, new InteractionSerializer());
//        gsonProvider.registerTypeAdapter(CollaboratorContext.class, new CollaboratorContextSerializer());
        gsonProvider.registerTypeAdapter(CollaboratorServer.class, new CollaboratorServerSerializer());
        gsonProvider.registerTypeAdapter(Level.class, new LevelSerializer());
    }

    @Override
    protected void registerSettings() {
        prefs.registerSetting(PREF_LOG_LEVEL, Level.class, Level.INFO, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(PREF_COLLABORATOR_ADDRESS, String.class, "burpcollaborator.net", Preferences.Visibility.GLOBAL);
        prefs.registerSetting(PREF_POLLING_ADDRESS, String.class, "polling.burpcollaborator.net", Preferences.Visibility.GLOBAL);
        prefs.registerSetting(PREF_POLLING_PORT, Integer.class, 443, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(PREF_REMOTE_SSL_ENABLED, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(PREF_IGNORE_CERTIFICATE_ERRORS, Boolean.class, false, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(PREF_SSL_HOSTNAME_VERIFICATION, Boolean.class, true, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(PREF_LOCAL_PORT, Integer.class, 32541, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(PREF_SECRET, String.class, "Your Secret String", Preferences.Visibility.GLOBAL);
        prefs.registerSetting(PREF_BLOCK_PUBLIC_COLLABORATOR, Boolean.class, false, Preferences.Visibility.PROJECT);
        prefs.registerSetting(PREF_PROXY_REQUESTS_WITH_BURP, Boolean.class, false, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(PREF_USE_AUTHENTICATION, Boolean.class, false, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(PREF_AUTO_START, Boolean.class, false, Preferences.Visibility.GLOBAL);
        prefs.registerSetting(PREF_ORIGINAL_COLLABORATOR_SETTINGS, String.class, "", Preferences.Visibility.PROJECT);
        prefs.registerSetting(PREF_COLLABORATOR_HISTORY, new TypeToken<ArrayList<CollaboratorServer>>(){}.getType(), new ArrayList<>(), Preferences.Visibility.PROJECT);
    }
}
