/*******************************************************************************
 * Mirakel is an Android App for managing your ToDo-Lists
 *
 * Copyright (c) 2013-2014 Anatolij Zelenin, Georg Semmler.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package de.azapps.mirakel.helper;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.azapps.mirakel.DefinitionsHelper;
import de.azapps.mirakel.model.MirakelInternalContentProvider;
import de.azapps.mirakel.model.list.ListMirakel;
import de.azapps.mirakel.model.task.Task;
import de.azapps.mirakel.model.task.TaskDeserializer;
import de.azapps.tools.Log;
import de.azapps.tools.OptionalUtils;

import static de.azapps.tools.OptionalUtils.withOptional;

public class UndoHistory {
    private static final short LIST = 1;
    private static String TAG = "UndoHistory";
    private static final short TASK = 0;
    public static final String UNDO = "OLD";

    public static void logCreate(final ListMirakel newList, final Context ctx) {
        updateLog(LIST, String.valueOf(newList.getId()), ctx);
    }

    public static void logCreate(final Task newTask, final Context ctx) {
        if (newTask == null) {
            return;
        }
        updateLog(TASK, String.valueOf(newTask.getId()), ctx);
    }

    public static void undoLast(final Context ctx) {
        final String last = MirakelCommonPreferences.getFromLog(0);
        if (last != null && !last.isEmpty()) {
            final short type = Short.parseShort(String.valueOf(last.charAt(0)));
            if (last.charAt(1) != '{') {
                try {
                    final long id = Long.parseLong(last.substring(1));
                    switch (type) {
                    case TASK:
                        withOptional(Task.get(id), new OptionalUtils.Procedure<Task>() {
                            @Override
                            public void apply(Task input) {
                                input.destroy(true);
                            }
                        });
                        break;
                    case LIST:
                        withOptional(ListMirakel.get(id), new OptionalUtils.Procedure<ListMirakel>() {
                            @Override
                            public void apply(ListMirakel input) {
                                input.destroy(true);
                            }
                        });
                        break;
                    default:
                        Log.wtf(TAG, "unkown Type");
                        break;
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "cannot parse String", e);
                }
            } else {
                final JsonObject json = new JsonParser().parse(
                    last.substring(1)).getAsJsonObject();
                switch (type) {
                case TASK:
                    final Gson gson = new GsonBuilder().registerTypeAdapter(
                        Task.class, new TaskDeserializer()).create();
                    final Task t = gson.fromJson(json, Task.class);
                    if (t.getId() != Task.INVALID_ID) {
                        t.save(false);
                        break;
                    }
                    try {
                        t.create(true, true);
                    } catch (DefinitionsHelper.NoSuchListException e) {
                        Log.w(TAG, "cannot restore task, list is missing", e);
                    }

                    break;
                case LIST:
                    final ListMirakel l = ListMirakel.unsafeParseJson(json);
                    if (l.getId() != ListMirakel.INVALID_ID) {
                        l.save(false);
                    } else {
                        try {
                            final ContentValues cv = l.getContentValues();
                            cv.remove(ListMirakel.ID);
                            ctx.getContentResolver()
                            .insert(MirakelInternalContentProvider.LIST_URI,
                                    cv);
                        } catch (final RuntimeException e) {
                            Log.e(TAG, "cannot restore List", e);
                        }
                    }
                    break;
                default:
                    Log.wtf(TAG, "unknown Type");
                    break;
                }
            }
        }
        final SharedPreferences.Editor editor = MirakelPreferences.getEditor();
        for (int i = 0; i < MirakelCommonPreferences.getUndoNumber(); i++) {
            final String old = MirakelCommonPreferences.getFromLog(i + 1);
            editor.putString(UNDO + i, old);
        }
        editor.putString(UNDO + MirakelCommonPreferences.getUndoNumber(), "");
        editor.commit();
    }

    public static void updateLog(final ListMirakel listMirakel,
                                 final Context ctx) {
        if (listMirakel != null) {
            updateLog(LIST, listMirakel.toJson(), ctx);
        }
    }

    private static void updateLog(final short type, final String json,
                                  final Context ctx) {
        if (ctx == null) {
            Log.e(TAG, "context is null");
            return;
        }
        // Log.d(TAG, json);
        final SharedPreferences.Editor editor = MirakelPreferences.getEditor();
        for (int i = MirakelCommonPreferences.getUndoNumber(); i > 0; i--) {
            final String old = MirakelCommonPreferences.getFromLog(i - 1);
            editor.putString(UNDO + i, old);
        }
        editor.putString(UNDO + 0, type + json);
        editor.commit();
    }

    public static void updateLog(final Task task, final Context ctx) {
        if (task != null) {
            updateLog(TASK, task.toJson(), ctx);
        }
    }

}
