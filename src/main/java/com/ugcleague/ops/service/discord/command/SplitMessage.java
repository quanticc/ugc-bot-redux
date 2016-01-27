package com.ugcleague.ops.service.discord.command;

import java.util.ArrayList;
import java.util.List;

public class SplitMessage {

    private final String message;

    public SplitMessage(String message) {
        this.message = message;
    }

    public List<String> split(int maxLength) {
        List<String> splits = new ArrayList<>();
        String str = message;
        int end;
        while (!str.isEmpty()) {
            if (str.length() <= maxLength) {
                splits.add(str);
                str = "";
            } else {
                end = Math.min(str.length(), str.lastIndexOf("\n", maxLength));
                if (end <= 0) {
                    end = Math.min(str.length(), maxLength);
                }
                splits.add(str.substring(0, end));
                str = str.substring(end);
            }
        }
        return splits;
    }
}
