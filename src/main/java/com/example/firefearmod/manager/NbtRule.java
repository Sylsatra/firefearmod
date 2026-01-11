package com.example.firefearmod.manager;

import com.electronwill.nightconfig.core.Config;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.GsonHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.regex.Pattern;

public record NbtRule(String path, String operator, String value, @Nullable String type) {
    private static final Logger LOGGER = LogManager.getLogger();

    public static NbtRule fromConfig(Config config) {
        String path = config.get("path");
        String operator = config.get("op");
        Object valObj = config.get("value");
        String value = valObj != null ? String.valueOf(valObj) : "";
        String type = config.getOptional("type").map(String::valueOf).orElse(null);
        return new NbtRule(path, operator, value, type);
    }

    public static NbtRule fromJson(JsonObject json) {
        String path = GsonHelper.getAsString(json, "path");
        String operator = GsonHelper.getAsString(json, "op");
        String value = "";
        if (json.has("value")) {
            if (json.get("value").isJsonPrimitive()) {
                value = json.get("value").getAsString();
            } else {
                value = json.get("value").toString();
            }
        }
        String type = json.has("type") ? GsonHelper.getAsString(json, "type") : null;
        return new NbtRule(path, operator, value, type);
    }

    public boolean matches(CompoundTag rootTag) {
        if (rootTag == null) return false;
        
        Tag currentTag = rootTag;
        String[] parts = path.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (currentTag instanceof CompoundTag compound) {
                if (!compound.contains(part)) {
                    return "exists".equalsIgnoreCase(operator) && "false".equalsIgnoreCase(value); 
                }
                currentTag = compound.get(part);
            } else {
                return false; 
            }
        }

        if (currentTag == null) {
             return "exists".equalsIgnoreCase(operator) && "false".equalsIgnoreCase(value);
        }

        return checkCondition(currentTag);
    }

    private boolean checkCondition(Tag tag) {
        String op = operator.toLowerCase();
        
        if ("exists".equals(op)) {
            return !"false".equalsIgnoreCase(value);

        }

        String tagValue = tag.getAsString();
        if (isNumeric(tagValue) && isNumeric(value)) {
            try {
                double tagNum = Double.parseDouble(tagValue.replaceAll("['\"]", "").replaceAll("[dfbisl]$", ""));
                double ruleNum = Double.parseDouble(value);
                return switch (op) {
                    case ">" -> tagNum > ruleNum;
                    case ">=" -> tagNum >= ruleNum;
                    case "<" -> tagNum < ruleNum;
                    case "<=" -> tagNum <= ruleNum;
                    case "==", "equals" -> Double.compare(tagNum, ruleNum) == 0;
                    case "!=" -> Double.compare(tagNum, ruleNum) != 0;
                    default -> false;
                };
            } catch (NumberFormatException ignored) {}
        }

        String strVal = tagValue.replaceAll("^\"|\"$", ""); 
        
        return switch (op) {
            case "==", "equals" -> Objects.equals(strVal, value);
            case "!=" -> !Objects.equals(strVal, value);
            case "contains" -> strVal.contains(value);
            case "matches", "regex" -> Pattern.compile(value).matcher(strVal).find();
            default -> false;
        };
    }
    
    private boolean isNumeric(String str) {
        if (str == null) return false;
        return str.matches("-?\\d+(\\.\\d+)?[dfbisl]?");
    }
}
