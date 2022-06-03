package plugin.ui.main.context;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import common.CommonStatic;
import common.io.WebFileIO;
import common.io.assets.UpdateCheck;
import main.Opts;
import page.LoadPage;
import plugin.ui.common.config.StaticConfig;
import plugin.ui.common.util.JsonUtils;
import plugin.ui.common.util.UIException;
import plugin.ui.main.UIPlugin;
import plugin.ui.main.UITheme;
import plugin.ui.main.util.UIDownloader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class UIContext {
    public static final Integer SUPPORTED_LEAST_PROFILE_VERSION = 22;
    private static UserProfile profile;
    private static UITheme theme;

    public static void init() {
        checkEnvironment();
        profile = getOrDefault(StaticConfig.UI_JSON_PATH, UserProfile.class);
        theme = getOrDefault(StaticConfig.THEME_JSON_PATH, UITheme.class);
        checkProfileVersion();
    }

    private static void checkProfileVersion() {
        Integer localVer = profile.basic.getInteger("version");
        if (localVer == null || SUPPORTED_LEAST_PROFILE_VERSION > localVer) {
            UIPlugin.pop("Deprecated profile [version=" + localVer + "], the old version ui.json will be overwritten.", "Info");
            profile = UserProfile.getDefault();
            toFile(profile, StaticConfig.UI_JSON_PATH);
        }
    }

    public static void checkEnvironment() {
        UIChecker.checkEnvironment();
    }

    private static <T> T getOrDefault(String path, Class<T> clazz) {
        T target = null;
        boolean write = true;
        File file = new File(path);
        if (file.exists()) {
            // from locale
            try {
                target = JsonUtils.fromFile(file, clazz);
            } catch (Exception e) {
                UIPlugin.popError("Invalid file: " + new File(path).getAbsolutePath() + "\n<html><h2>Advice: delete it.\n<html><h3>Reason:" + e);
                e.printStackTrace();
                write = false;
            }
        }

        if (target == null) {
            // from classpath
            try {
                target = JsonUtils.fromClasspath(path, clazz);
                // if (write)
                    // toFile(target, path);
            } catch (Exception e) {
                throw new UIException("Failed to init " + path + ": " + e);
            }
        }

        return target;
    }

    public static void writeData() {
        toFile(profile, StaticConfig.UI_JSON_PATH);
        toFile(theme, StaticConfig.THEME_JSON_PATH);
    }

    private static void toFile(Object o, String path) {
        try {
            JsonUtils.toFile(o, path);
        } catch (IOException e) {
            e.printStackTrace();
            UIPlugin.popError("failed to write " + path);
        }
    }

    public static ThemeConfig getThemeConfig() {
        return profile.theme;
    }

    public static BasicConfig getBasicConfig() {
        return profile.basic;
    }

    public static UITheme getTheme() {
        return theme;
    }

    public static void askUpdate() {
        UIChecker.askUpdate();
    }

    public static void checkUpdate() {
        UIChecker.checkUpdate();
    }

    public abstract static class UIChecker {
        private static final String LIB_URL = "https://repo1.maven.org/maven2/com/formdev/";
        private static final String LIB_DIRECTORY = "./BCU_lib/";
        private static final String JAR_CHECK_URL = "https://raw.githubusercontent.com/hect0x7/bcu-ui/main/check.json";
        private static final String[] UILibs = {
                "flatlaf-intellij-themes-2.2.jar", "flatlaf-2.2.jar"
        };

        public static void checkEnvironment() {
            // check ui dir
            File file = new File(StaticConfig.UI_DIRECTORY);
            if (!file.exists()) {
                boolean mkdir = file.mkdir();
                if (!mkdir) {
                    throw new UIException("failed to create ui dir.");
                }
            }

            // check ui lib
            checkLib();
        }

        public static String getURL(String lib) {
            int index = lib.lastIndexOf("-");
            String version = lib.substring(index + 1, lib.length() - ".jar".length());
            String artifacts = lib.substring(0, index);
            return LIB_URL + artifacts + "/" + version + "/" + lib;
        }

        public static List<UpdateCheck.Downloader> getMissingLib() {
            List<UpdateCheck.Downloader> downloaderList = new ArrayList<>();
            for (String lib : UILibs) {
                File libFile = new File(LIB_DIRECTORY + lib);
                if (!libFile.exists()) {
                    String url = getURL(lib);
                    downloaderList.add(new UpdateCheck.Downloader(libFile, new File("./BCU_lib/.jar.temp"),
                            "download UI library: " + lib, false, url)
                    );
                }
            }
            return downloaderList;
        }

        private static void checkLib() {
            // get missing lib
            List<UpdateCheck.Downloader> missingLib = getMissingLib();

            if (missingLib.size() == 0) {
                return;
            }

            // inquiry
            if (Opts.conf("BCU needs to download necessary UI lib before access, do you accept?")) {
                // result
                UIDownloader.downloadLibs(missingLib);
                Opts.pop("Download UI library successfully, please restart BCU", "success");
                // TODO: 2022/5/26 call restart method
            }

            CommonStatic.def.save(true, true);
        }

        public static void checkUpdate() {
            LoadPage.prog("checking UI update information");
            // get update json
            JsonObject json = getUpdateJson();

            if (json == null) {
                return;
            }

            UpdateJson uj = JsonUtils.get("release/latest", json, UpdateJson.class);

            // inquiry
            if (uj.version.compareTo(UIPlugin.PLUGIN_VERSION) > 0 && uj.forceUpdate) {
                String popText = "New BCU file update found: " + uj.getArtifact() +
                        ", do you want to update jar file?\n" + uj.getDescription();
                boolean updateIt = Opts.conf(popText);
                // result
                if (updateIt) {
                    UIDownloader.downloadJar(getDownloader(uj), true);
                }
            }

        }

        private static JsonObject getUpdateJson() {
            JsonElement json = null;

            try {
                json = WebFileIO.read(JAR_CHECK_URL);
            } catch (Exception ignored) {
                UIPlugin.popError("Failed to check update, try again later on a stable WI-FI connection");
            }

            return (JsonObject) json;
        }

        public static void askUpdate() {
            // get update json
            JsonObject json = getUpdateJson();

            if (json == null) {
                return;
            }

            UpdateJson uj = JsonUtils.get("release/latest", json, UpdateJson.class);

            // inquiry
            if (uj.version.compareTo(UIPlugin.PLUGIN_VERSION) > 0) {
                String popText = "New BCU Jar file update found: " + uj.getArtifact()
                        + ", do you want to update?" + " Its' " + (uj.forceUpdate ? "necessary.\n" : "unnecessary.\n")
                        + uj.getDescription();
                boolean updateIt = Opts.conf(popText);
                // result
                if (updateIt) {
                    UpdateCheck.Downloader d = getDownloader(uj);
                    UIDownloader.downloadJar(d, false, "url: " + uj.url, d.desc);
                }
            } else {
                Opts.pop("Your BCU is the latest version.\n" + uj.getDescription(), "RESULT");
            }
        }

        private static UpdateCheck.Downloader getDownloader(UpdateJson updateJson) {
            String filename = updateJson.getArtifact();
            File target = new File("./" + filename);
            return new UpdateCheck.Downloader(target,
                    new File("./temp.temp"),
                    "Downloading " + filename + "...",
                    true, updateJson.url);
        }

        public static class UpdateJson {

            public String version;

            public Boolean forceUpdate;

            public String url;

            public String info;

            public String getArtifact() {
                return "BCU-" + version.replace(".", "-").substring(1) + ".jar";
            }

            public String getDescription() {
                return this.version + " info: \n" + this.info;
            }

            @Override
            public String toString() {
                return JsonUtils.G.toJson(this);
            }
        }

    }
}
