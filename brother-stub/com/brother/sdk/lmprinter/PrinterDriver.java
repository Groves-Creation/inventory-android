package com.brother.sdk.lmprinter;

import android.graphics.Bitmap;
import com.brother.sdk.lmprinter.setting.PrintSettings;

public class PrinterDriver {
    public void closeChannel() { }

    public PrintError printImage(final String path, final PrintSettings printSettings) { return null; }

    public PrintError printImage(final String[] paths, final PrintSettings printSettings) { return null; }

    public PrintError printImage(final Bitmap bitmap, final PrintSettings printSettings) { return null; }

    public void cancelPrinting() { }

    public GetStatusResult getPrinterStatus() { return null; }
}
