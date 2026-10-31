package com.qcl.launcher.launcher.dialogs.control;

import static android.app.Activity.RESULT_OK;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.tungsten.filepicker.Constants;
import com.tungsten.filepicker.FileChooser;
import com.qcl.launcher.R;
import com.qcl.launcher.control.importer.ExternalLayoutImporter;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.dialogs.LoadingDialog;
import com.qcl.launcher.launcher.list.local.controller.ControlPattern;
import com.qcl.launcher.launcher.list.local.controller.ControlPatternListAdapter;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.launcher.setting.SettingUtils;
import com.qcl.launcher.utils.file.FileStringUtils;
import com.qcl.launcher.utils.file.FileUtils;
import com.qcl.launcher.utils.file.UriUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class ControllerManagerDialog extends Dialog implements View.OnClickListener {

    public static final int IMPORT_PATTERN_REQUEST_CODE = 3200;
    public static final int IMPORT_PATTERN_REQUEST_CODE_ISOLATED = 3900;

    private MainActivity activity;
    private boolean fullscreen;
    public String currentPattern;
    public OnPatternChangeListener onPatternChangeListener;
    private boolean isolate;

    private ListView patternList;
    private Button importPattern;
    private Button createNewPattern;
    private Button exit;

    public ControllerManagerDialog(@NonNull Context context, MainActivity activity, boolean fullscreen, String currentPattern, OnPatternChangeListener onPatternChangeListener,boolean isolate) {
        super(context);
        this.activity = activity;
        this.fullscreen = fullscreen;
        this.currentPattern = currentPattern;
        this.onPatternChangeListener = onPatternChangeListener;
        this.isolate = isolate;
        setContentView(R.layout.dialog_manage_controller);
        setCancelable(false);
        init();
    }

    private void init(){
        patternList = findViewById(R.id.control_pattern_list);
        importPattern = findViewById(R.id.import_pattern);
        createNewPattern = findViewById(R.id.new_pattern);
        exit = findViewById(R.id.exit);

        importPattern.setOnClickListener(this);
        createNewPattern.setOnClickListener(this);
        exit.setOnClickListener(this);

        loadList();
    }

    public void loadList(){
        ArrayList<ControlPattern> list = SettingUtils.getControlPatternList();
        ControlPatternListAdapter adapter = new ControlPatternListAdapter(getContext(),activity,this,list,currentPattern,fullscreen,isolate);
        patternList.setAdapter(adapter);
    }

    public void onResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        LoadingDialog dialog = new LoadingDialog(getContext());
        dialog.setLoadingText(getContext().getString(R.string.dialog_manage_controller_import_dialog));
        new Thread(() -> {
            activity.runOnUiThread(dialog::show);
            String successMessage = null;
            String errorMessage = null;
            File importDir = new File(AppManifest.DEFAULT_CACHE_DIR, "import");
            try {
                Uri uri = data.getData();
                if (uri == null) {
                    throw new IOException("empty uri");
                }
                String path = UriUtils.getRealPathFromUri_AboveApi19(getContext(), uri);
                if (path == null || path.isEmpty()) {
                    throw new IOException("unreadable path");
                }
                File sourceFile = new File(path);
                String fileName = sourceFile.getName();
                String baseName = fileName;
                int dot = fileName.lastIndexOf('.');
                if (dot > 0) {
                    baseName = fileName.substring(0, dot);
                }

                FileUtils.deleteDirectory(importDir.getAbsolutePath());
                FileUtils.createDirectory(importDir.getAbsolutePath());
                File cacheFile = new File(importDir, fileName);
                FileUtils.copyFile(path, cacheFile.getAbsolutePath());

                ExternalLayoutImporter.Result result;
                if (fileName.toLowerCase(Locale.ROOT).endsWith(".hmclpe")
                        || ExternalLayoutImporter.isZip(cacheFile)) {
                    result = ExternalLayoutImporter.parseHmclPeZip(cacheFile,
                            new File(importDir, "unzip"), baseName);
                } else {
                    result = ExternalLayoutImporter.parseJson(
                            FileStringUtils.getStringFromFile(cacheFile.getAbsolutePath()), baseName);
                }
                String savedName = ExternalLayoutImporter.saveAsNewPattern(getContext(), result);
                successMessage = getContext().getString(
                        R.string.dialog_manage_controller_import_success, result.format, savedName);
            } catch (Throwable t) {
                android.util.Log.w("jrelog", "[控键导入] 失败", t);
                errorMessage = t.getMessage();
                if (errorMessage == null || errorMessage.trim().isEmpty()) {
                    errorMessage = t.getClass().getSimpleName();
                }
            } finally {
                FileUtils.deleteDirectory(importDir.getAbsolutePath());
            }
            final String ok = successMessage;
            final String error = errorMessage;
            activity.runOnUiThread(() -> {
                dialog.dismiss();
                if (ok != null) {
                    Toast.makeText(getContext(), ok, Toast.LENGTH_LONG).show();
                    loadList();
                } else {
                    Toast.makeText(getContext(),
                            getContext().getString(R.string.dialog_manage_controller_import_error)
                                    + (error == null ? "" : " (" + error + ")"),
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    @Override
    public void onClick(View view) {
        if (view == importPattern){
            Intent intent = new Intent(getContext(), FileChooser.class);
            intent.putExtra(Constants.SELECTION_MODE, Constants.SELECTION_MODES.SINGLE_SELECTION.ordinal());
            intent.putExtra(Constants.ALLOWED_FILE_EXTENSIONS, "hmclpe;json;zip");
            intent.putExtra(Constants.INITIAL_DIRECTORY, new File(Environment.getExternalStorageDirectory().getAbsolutePath()).getAbsolutePath());
            // 实例独立设置页（VersionSettingUI）只转发 3900，全局设置页转发 3200
            activity.startActivityForResult(intent,
                    isolate ? IMPORT_PATTERN_REQUEST_CODE_ISOLATED : IMPORT_PATTERN_REQUEST_CODE);
        }
        if (view == createNewPattern){
            CreateControlPatternDialog dialog = new CreateControlPatternDialog(getContext(),activity, new CreateControlPatternDialog.OnPatternCreateListener() {
                @Override
                public void OnPatternCreate(ControlPattern controlPattern) {
                    FileUtils.createDirectory(AppManifest.CONTROLLER_DIR + "/" + controlPattern.name);
                    Gson gson = new Gson();
                    String string = gson.toJson(controlPattern);
                    FileStringUtils.writeFile(AppManifest.CONTROLLER_DIR + "/" + controlPattern.name + "/info.json",string);
                    loadList();
                }
            });
            dialog.show();
        }
        if (view == exit){
            dismiss();
        }
    }

    public interface OnPatternChangeListener{
        void onPatternChange(String pattern);
    }

}
