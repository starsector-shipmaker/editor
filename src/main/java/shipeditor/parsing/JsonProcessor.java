package shipeditor.parsing;

import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Log4j2
public final class JsonProcessor {



    private JsonProcessor() {
    }

    public static String straightenMalformed(File input) {
        String text = JsonProcessor.readFile(input);
        return straightenMalformedText(text);
    }

    public static String straightenMalformedText(String input) {
        if (input == null || input.isEmpty()) return "";
        
        StringBuilder sb = new StringBuilder(input.length() + 128);
        int len = input.length();
        boolean inQuotes = false;
        boolean escape = false;

        for (int i = 0; i < len; ) {
            char c = input.charAt(i);

            // 1. Quoted block state processing
            if (inQuotes) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inQuotes = false;
                }
                sb.append(c);
                i++;
                continue;
            }

            // 2. We are outside quotes
            if (c == '"') {
                inQuotes = true;
                sb.append(c);
                i++;
                continue;
            }

            // Skip comments starting with '#'
            if (c == '#') {
                while (i < len && input.charAt(i) != '\n' && input.charAt(i) != '\r') {
                    i++;
                }
                continue;
            }

            // Translate semicolons to commas and skip redundant consecutive commas
            if (c == ';' || c == ',') {
                int lastIdx = sb.length() - 1;
                while (lastIdx >= 0) {
                    char prev = sb.charAt(lastIdx);
                    if (prev == ' ' || prev == '\t' || prev == '\n' || prev == '\r') {
                        lastIdx--;
                    } else {
                        break;
                    }
                }
                if (lastIdx >= 0) {
                    char prev = sb.charAt(lastIdx);
                    if (prev != ',' && prev != '{' && prev != '[') {
                        sb.append(',');
                    }
                }
                i++;
                continue;
            }

            // Process Numbers (strip trailing f/d suffixes and trailing periods)
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.') {
                int start = i;
                int curr = i;
                boolean hasDigit = false;

                loop:
                while (curr < len) {
                    char nc = input.charAt(curr);
                    switch (nc) {
                        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                            hasDigit = true;
                            curr++;
                        }
                        case '.' -> curr++;
                        case 'e', 'E' -> {
                            curr++;
                            if (curr < len && (input.charAt(curr) == '+' || input.charAt(curr) == '-')) {
                                curr++;
                            }
                        }
                        default -> {
                            break loop;
                        }
                    }
                }

                if (hasDigit) {
                    int numEnd = curr;

                    // Strip suffix if present (f/F/d/D)
                    if (curr < len) {
                        char nc = input.charAt(curr);
                        if (nc == 'f' || nc == 'F' || nc == 'd' || nc == 'D') {
                            if (curr + 1 == len || !isWordChar(input.charAt(curr + 1))) {
                                curr++; // consume the suffix
                            }
                        }
                    }

                    // Strip trailing period if present
                    if (numEnd > start && input.charAt(numEnd - 1) == '.') {
                        numEnd--;
                    }

                    sb.append(input, start, numEnd);
                    i = curr;
                    continue;
                }
            }

            // Check if it's the start of an identifier: [a-zA-Z_] at a word boundary, and not preceded by a dot
            if (((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_') &&
                (i == 0 || (input.charAt(i - 1) != '.' && !isWordChar(input.charAt(i - 1))))) {
                int start = i;
                while (i < len && isWordChar(input.charAt(i))) {
                    i++;
                }
                String word = input.substring(start, i);

                // Check if this word should be quoted
                boolean shouldQuote = true;
                if ("true".equals(word) || "false".equals(word) || "null".equals(word)) {
                    shouldQuote = false;
                }
                // Exclude words immediately preceded or followed by double quotes
                if (start > 0 && input.charAt(start - 1) == '"') {
                    shouldQuote = false;
                }
                if (i < len && input.charAt(i) == '"') {
                    shouldQuote = false;
                }

                if (shouldQuote) {
                    sb.append('"').append(word).append('"');
                } else {
                    sb.append(word);
                }
                continue;
            }

            // Strip trailing commas before closing brackets/braces
            if (c == ']' || c == '}') {
                int lastIdx = sb.length() - 1;
                while (lastIdx >= 0) {
                    char prev = sb.charAt(lastIdx);
                    if (prev == ' ' || prev == '\t' || prev == '\n' || prev == '\r') {
                        lastIdx--;
                    } else {
                        break;
                    }
                }
                if (lastIdx >= 0 && sb.charAt(lastIdx) == ',') {
                    sb.deleteCharAt(lastIdx);
                }
            }

            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static String readFile(File input) {
        if (input == null || !input.exists()) {
            log.error("Failed to read file: file does not exist: {}", input != null ? input.getPath() : "null");
            return "";
        }
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(input.toPath());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read file: {}", input.getName(), e);
            return "";
        }
    }

    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }



}
