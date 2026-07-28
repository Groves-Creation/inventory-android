package com.brother.sdk.lmprinter;

public class PrintError {
    public enum ErrorCode {
        NoError,
        PrintSettingsError,
        FilepathURLError,
        PDFPageError,
        PrintSettingsNotSupportError,
        DataBufferError,
        PrinterModelError,
        WorkPathError,
        Canceled,
        ChannelTimeout,
        SetModelError,
        UnsupportedFile,
        SetMarginError,
        SetLabelSizeError,
        CustomPaperSizeError,
        SetLengthError,
        TubeSettingError,
        ChannelErrorStreamStatusError,
        ChannelErrorUnsupportedChannel,
        PrinterStatusErrorPaperEmpty,
        PrinterStatusErrorCoverOpen,
        PrinterStatusErrorBusy,
        PrinterStatusErrorPrinterTurnedOff,
        PrinterStatusErrorBatteryWeak,
        PrinterStatusErrorExpansionBufferFull,
        PrinterStatusErrorCommunicationError,
        PrinterStatusErrorPaperJam,
        PrinterStatusErrorMediaCannotBeFed,
        PrinterStatusErrorOverHeat,
        PrinterStatusErrorHighVoltageAdapter,
        PrinterStatusErrorUnsupportedCharger,
        PrinterStatusErrorIncompatibleOptionalEquipment,
        PrinterStatusErrorUnknownError,
        UnknownError,
    }

    public ErrorCode getCode() { return null; }

    public String getErrorDescription() { return null; }
}
