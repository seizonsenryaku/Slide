package me.ccrama.redditslide;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import net.dean.jraw.models.CommentSort;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.meta.SubmissionSerializer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by carlo_000 on 11/19/2015.
 */
public class OfflineSubreddit {

    public static Long currentid = 0L;
    public long time;
    public ArrayList<Submission> submissions;
    public String subreddit;
    public boolean base;

    public static void writeSubmission(JsonNode node, Submission s, Context c) {
        writeSubmissionToStorage(s, node, c);
    }

    static File cacheDirectory;

    public static File getCacheDirectory(Context context) {
        if (cacheDirectory == null) {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) && context.getExternalCacheDir() != null) {
                cacheDirectory = context.getExternalCacheDir();
            }

            cacheDirectory = context.getCacheDir();
        }
        return cacheDirectory;
    }

    public OfflineSubreddit overwriteSubmissions(List<Submission> data) {
        submissions = new ArrayList<>(data);
        return this;
    }

    public static void writeSubmissionToStorage(Submission s, JsonNode node, Context c) {
        File toStore = new File(getCacheDirectory(c) + File.separator + s.getFullName());
        try {
            FileWriter writer = new FileWriter(toStore);
            writer.append(node.toString());
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isStored(String name, Context c) {
        return new File(getCacheDirectory(c) + File.separator + name).exists();
    }

    public void writeToMemory(Context c) {
        if (cache == null)
            cache = new HashMap<>();
        if (subreddit != null) {
            String title = subreddit.toLowerCase() + "," + (base ? 0 : time);
            String fullNames = "";
            for (Submission sub : submissions) {
                fullNames += sub.getFullName() + ",";
                if (!isStored(sub.getFullName(), c))
                    writeSubmissionToStorage(sub, sub.getDataNode(), c);
            }
            if (fullNames.length() > 0)
                Reddit.cachedData.edit().putString(title, fullNames.substring(0, fullNames.length() - 1)).apply();

            cache.put(title, this);
        }
    }

    public void writeToMemoryNoStorage() {
        if (cache == null)
            cache = new HashMap<>();
        if (subreddit != null) {
            String title = subreddit.toLowerCase() + "," + (base ? 0 : time);
            String fullNames = "";
            for (Submission sub : submissions) {
                fullNames += sub.getFullName() + ",";
            }
            if (fullNames.length() > 0)
                Reddit.cachedData.edit().putString(title, fullNames.substring(0, fullNames.length() - 1)).apply();
            cache.put(title, this);
        }
    }

    public void writeToMemoryAsync(final Context c) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                writeToMemory(c);
                return null;
            }
        }.execute();
    }

    public void writeToMemory(ArrayList<String> names) {
        if (subreddit != null && !names.isEmpty()) {
            String title = subreddit.toLowerCase() + "," + (time);
            String fullNames = "";
            for (String sub : names) {
                fullNames += sub + ",";
            }
            Reddit.cachedData.edit().putString(title, fullNames.substring(0, fullNames.length() - 1)).apply();
        }
    }

    public static OfflineSubreddit getSubreddit(String subreddit, boolean offline, Context c) {
        return getSubreddit(subreddit, 0L, offline, c);
    }

    public static OfflineSubreddit getSubNoLoad(String s) {
        s = s.toLowerCase();

        OfflineSubreddit o = new OfflineSubreddit();
        o.subreddit = s.toLowerCase();
        o.base = true;
        o.time = 0;
        o.submissions = new ArrayList<>();
        return o;
    }

    private static HashMap<String, OfflineSubreddit> cache;

    public static OfflineSubreddit getSubreddit(String subreddit, Long time, boolean offline, Context c) {
        if (cache == null)
            cache = new HashMap<>();
        String title;
        if (subreddit != null)
            title = subreddit.toLowerCase() + "," + time;
        else {
            title = "";
            subreddit = "";
        }
        if (cache.containsKey(title)) {
            return cache.get(title);
        } else {
            subreddit = subreddit.toLowerCase();

            OfflineSubreddit o = new OfflineSubreddit();
            o.subreddit = subreddit.toLowerCase();

            if (time == 0) {
                o.base = true;
            }

            o.time = time;

            String[] split = Reddit.cachedData.getString(subreddit.toLowerCase() + "," + time, "").split(",");
            if (split.length > 1) {
                o.time = time;
                o.submissions = new ArrayList<>();
                ObjectMapper mapperBase = new ObjectMapper();
                ObjectReader reader = mapperBase.reader();

                for (String s : split) {
                    if (!s.contains("_")) s = "t3_" + s;
                    if (!s.isEmpty()) {
                        try {
                            Submission sub = getSubmissionFromStorage(s, c, offline, reader);
                            if (sub != null) {
                                o.submissions.add(sub);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }

            } else {
                o.submissions = new ArrayList<>();
            }
            cache.put(title, o);
            return o;

        }
    }

    public static Submission getSubmissionFromStorage(String fullName, Context c, boolean offline, ObjectReader reader) throws IOException {
        String gotten = getStringFromFile(fullName, c);
        if (!gotten.isEmpty()) {
            if (gotten.startsWith("[") && offline) {
                return (SubmissionSerializer.withComments(reader.readTree(gotten), CommentSort.CONFIDENCE));
            } else if (gotten.startsWith("[")) {
                JsonNode elem = reader.readTree(gotten);
                return (new Submission(elem.get(0).get("data").get("children").get(0).get("data")));
            } else {
                return (new Submission(reader.readTree(gotten)));
            }
        }
        return null;
    }

    public static String getStringFromFile(String name, Context c) {
        File f = new File(getCacheDirectory(c) + File.separator + name);
        if (f.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(f));
                char[] chars = new char[(int) f.length()];
                reader.read(chars);
                reader.close();
                return new String(chars);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            return "";
        }
        return "";
    }

    public static OfflineSubreddit newSubreddit(String subreddit) {
        subreddit = subreddit.toLowerCase();

        OfflineSubreddit o = new OfflineSubreddit();
        o.subreddit = subreddit.toLowerCase();
        o.base = false;
        o.time = System.currentTimeMillis();
        o.submissions = new ArrayList<>();

        return o;

    }

    public void clearPost(Submission s) {
        if (submissions != null) {
            Submission toRemove = null;
            for (Submission s2 : submissions) {
                if (s.getFullName().equals(s2.getFullName())) {
                    toRemove = s2;
                }
            }
            if (toRemove != null) {
                submissions.remove(toRemove);
            }
        }
    }

    int savedIndex;
    Submission savedSubmission;

    public void hide(int index) {
        hide(index, true);
    }

    public void hide(int index, boolean save) {
        if (submissions != null) {
            savedSubmission = submissions.get(index);
            submissions.remove(index);
            if (save) {
                savedIndex = index;
                writeToMemoryNoStorage();
            }
        }
    }

    public void unhideLast() {
        if (submissions != null && savedSubmission != null) {
            submissions.add(savedIndex, savedSubmission);
            writeToMemoryNoStorage();
        }
    }

    public static ArrayList<String> getAll(String subreddit) {
        subreddit = subreddit.toLowerCase();
        ArrayList<String> keys = new ArrayList<>();
        for (String s : Reddit.cachedData.getAll().keySet()) {
            if (s.startsWith(subreddit) && s.contains(",")) {
                keys.add(s);
            }
        }

        return keys;
    }

    public static ArrayList<String> getAll() {
        ArrayList<String> keys = new ArrayList<>();
        for (String s : Reddit.cachedData.getAll().keySet()) {
            if (s.contains(",") && !s.startsWith("multi")) {
                keys.add(s);
            }
        }
        return keys;
    }

    public static ArrayList<String> getAllFormatted() {
        ArrayList<String> keys = new ArrayList<>();
        for (String s : Reddit.cachedData.getAll().keySet()) {
            if (s.contains(",") && !keys.contains(s.substring(0, s.indexOf(","))) && !s.startsWith("multi")) {
                keys.add(s.substring(0, s.indexOf(",")));
            }
        }
        return keys;
    }
}
