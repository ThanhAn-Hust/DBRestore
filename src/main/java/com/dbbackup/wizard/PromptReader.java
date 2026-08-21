package com.dbbackup.wizard;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

public class PromptReader {
    private final Scanner scanner;
    private final PrintStream out;

    public PromptReader() {
        this(System.in, System.out);
    }

    public PromptReader(InputStream in, PrintStream out) {
        this.scanner = new Scanner(in);
        this.out = out;
    }

    public PrintStream getOut() {
        return out;
    }

    public String readString(String prompt, String defaultValue) {
        if (defaultValue != null && !defaultValue.isBlank()) {
            out.print(AnsiColor.cyan(prompt) + " [" + AnsiColor.yellow(defaultValue) + "]: ");
        } else {
            out.print(AnsiColor.cyan(prompt) + ": ");
        }
        out.flush();
        if (!scanner.hasNextLine()) {
            return defaultValue;
        }
        String input = scanner.nextLine().trim();
        return input.isEmpty() ? defaultValue : input;
    }

    public String readPassword(String prompt) {
        out.print(AnsiColor.cyan(prompt) + ": ");
        out.flush();
        if (System.console() != null) {
            char[] chars = System.console().readPassword();
            return chars != null ? new String(chars) : "";
        }
        if (!scanner.hasNextLine()) {
            return "";
        }
        return scanner.nextLine();
    }

    public int readInt(String prompt, int min, int max, int defaultValue) {
        while (true) {
            String input = readString(prompt, String.valueOf(defaultValue));
            try {
                int val = Integer.parseInt(input);
                if (val >= min && val <= max) {
                    return val;
                }
            } catch (NumberFormatException ignored) {
            }
            out.println(AnsiColor.red("Please enter a valid number between " + min + " and " + max));
        }
    }

    public boolean readBoolean(String prompt, boolean defaultYes) {
        String def = defaultYes ? "Y/n" : "y/N";
        out.print(AnsiColor.cyan(prompt) + " [" + AnsiColor.yellow(def) + "]: ");
        out.flush();
        if (!scanner.hasNextLine()) {
            return defaultYes;
        }
        String input = scanner.nextLine().trim().toLowerCase();
        if (input.isEmpty()) {
            return defaultYes;
        }
        return input.startsWith("y");
    }

    public int readChoice(String title, List<String> options, int defaultOption) {
        out.println();
        out.println(AnsiColor.bold(title));
        for (int i = 0; i < options.size(); i++) {
            out.println("  " + AnsiColor.green("[" + (i + 1) + "]") + " " + options.get(i));
        }
        return readInt("Select an option", 1, options.size(), defaultOption) - 1;
    }

    public void waitForEnter(String message) {
        out.println();
        out.print(AnsiColor.yellow(message));
        out.flush();
        if (scanner.hasNextLine()) {
            scanner.nextLine();
        }
    }

    public void printSuccess(String msg) {
        out.println(AnsiColor.green("✔ " + msg));
    }

    public void printError(String msg) {
        out.println(AnsiColor.red("✖ " + msg));
    }

    public void printInfo(String msg) {
        out.println(AnsiColor.cyan("ℹ " + msg));
    }
}