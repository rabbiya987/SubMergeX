import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class LiveChecker {
    private static final Map<String, String> LIVE_CHECK_COMMANDS = new HashMap<>();
    
    static {
        LIVE_CHECK_COMMANDS.put("httprobe", "httprobe -c 50");
        LIVE_CHECK_COMMANDS.put("httpx", "httpx -silent -threads 100");
        LIVE_CHECK_COMMANDS.put("custom", "");
    }
    
    private int connectionTimeout = 5000;
    private int maxThreads = 50;
    
    public Set<String> checkLiveSubdomains(Set<String> subdomains, Scanner scanner, String outputFolder) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("Starting live subdomain checking...");
        System.out.println("=".repeat(50));
        
        String toolChoice = configureLiveCheckingTool(scanner);
        
        if (toolChoice.equals("skip")) {
            System.out.println("Skipping live checking");
            return new HashSet<>();
        }
        
        Set<String> liveSubdomains;
        
        if (toolChoice.equals("httprobe") || toolChoice.equals("httpx")) {
            liveSubdomains = checkLiveWithExternalTool(subdomains, toolChoice, outputFolder);
        } else {
            liveSubdomains = checkLiveWithJava(subdomains);
        }
        
        displayLiveCheckSummary(liveSubdomains, subdomains);
        return liveSubdomains;
    }
    
    private String configureLiveCheckingTool(Scanner scanner) {
        System.out.println("Live Subdomain Checking Configuration:");
        System.out.println("=".repeat(45));
        System.out.println("Available live checking tools:");
        System.out.println("1. httprobe (recommended) - Fast HTTP probe");
        System.out.println("2. httpx - Feature-rich HTTP toolkit");
        System.out.println("3. java - Built-in Java HTTP checking");
        System.out.println("4. custom - Define your own command");
        System.out.println("5. skip - Skip live checking");
        
        while (true) {
            System.out.print("Select tool (1-5, default: httprobe): ");
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "":
                case "1":
                    if (isToolInstalled("httprobe")) {
                        return "httprobe";
                    } else {
                        System.out.println("httprobe not found, falling back to Java checking");
                        return "java";
                    }
                case "2":
                    if (isToolInstalled("httpx")) {
                        return "httpx";
                    } else {
                        System.out.println("httpx not found, falling back to Java checking");
                        return "java";
                    }
                case "3":
                    return "java";
                case "4":
                    return configureCustomCommand(scanner);
                case "5":
                    return "skip";
                default:
                    System.out.println("Invalid choice. Please select 1-5.");
            }
        }
    }
    
    private String configureCustomCommand(Scanner scanner) {
        System.out.print("Enter custom command (subdomains will be piped to it): ");
        String command = scanner.nextLine().trim();
        if (!command.isEmpty()) {
            return "custom:" + command;
        } else {
            System.out.println("No command provided, using Java checking");
            return "java";
        }
    }
    
    private boolean isToolInstalled(String toolName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", toolName);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private Set<String> checkLiveWithExternalTool(Set<String> subdomains, String tool, String outputFolder) {
        System.out.println("Checking live subdomains with " + tool + "...");
        Set<String> liveSubdomains = new HashSet<>();
        
        try {
            String tempFile = outputFolder + "/temp_subdomains.txt";
            try (PrintWriter writer = new PrintWriter(tempFile)) {
                for (String subdomain : subdomains) {
                    writer.println(subdomain);
                }
            }
            
            String command = LIVE_CHECK_COMMANDS.get(tool);
            String fullCommand = "cat " + tempFile + " | " + command;
            System.out.println("Running: " + fullCommand);
            
            long startTime = System.currentTimeMillis();
            
            ProcessBuilder builder = new ProcessBuilder("bash", "-c", fullCommand);
            Process process = builder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        String domain = line.replaceAll("^https?://", "").split("/")[0];
                        liveSubdomains.add(domain);
                        System.out.println("Live: " + line);
                    }
                }
            }
            
            process.waitFor();
            long endTime = System.currentTimeMillis();
            
            new File(tempFile).delete();
            
            System.out.printf("Live check completed in %.2fs\n", (endTime - startTime) / 1000.0);
            
        } catch (Exception e) {
            System.err.println("Error during external tool checking: " + e.getMessage());
            System.out.println("Falling back to Java-based checking...");
            return checkLiveWithJava(subdomains);
        }
        
        return liveSubdomains;
    }
    
    private Set<String> checkLiveWithJava(Set<String> subdomains) {
        System.out.println("Checking live subdomains with Java HTTP...");
        Set<String> liveSubdomains = Collections.synchronizedSet(new HashSet<>());
        ExecutorService executor = Executors.newFixedThreadPool(maxThreads);
        List<Future<Void>> futures = new ArrayList<>();
        
        long startTime = System.currentTimeMillis();
        
        for (String subdomain : subdomains) {
            Future<Void> future = executor.submit(() -> {
                if (isSubdomainLive(subdomain)) {
                    liveSubdomains.add(subdomain);
                    System.out.printf("Live: %s\n", subdomain);
                }
                return null;
            });
            futures.add(future);
        }
        
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                System.err.println("Error during live check: " + e.getMessage());
            }
        }
        
        executor.shutdown();
        long endTime = System.currentTimeMillis();
        
        System.out.printf("Live check completed in %.2fs\n", (endTime - startTime) / 1000.0);
        
        return liveSubdomains;
    }
    
    private boolean isSubdomainLive(String subdomain) {
        String[] protocols = {"https://", "http://"};
        
        for (String protocol : protocols) {
            try {
                URL url = new URL(protocol + subdomain);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(connectionTimeout);
                connection.setReadTimeout(connectionTimeout);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "SubMergeX/2.1");
                
                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 400) {
                    return true;
                }
            } catch (Exception e) {
                // Continue to next protocol
            }
        }
        return false;
    }
    
    private void displayLiveCheckSummary(Set<String> liveSubdomains, Set<String> totalSubdomains) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("LIVE CHECKING COMPLETE");
        System.out.println("=".repeat(50));
        System.out.printf("Found %d/%d live subdomains", liveSubdomains.size(), totalSubdomains.size());
        
        if (totalSubdomains.size() > 0) {
            double percentage = (double) liveSubdomains.size() / totalSubdomains.size() * 100;
            System.out.printf(" (%.1f%%)\n", percentage);
        } else {
            System.out.println();
        }
        
        if (liveSubdomains.size() <= 15) {
            System.out.println("\nLive subdomains:");
            liveSubdomains.stream().sorted().forEach(s -> System.out.println("  • " + s));
        } else {
            System.out.println("\nSample live subdomains (first 15):");
            liveSubdomains.stream().sorted().limit(15)
                         .forEach(s -> System.out.println("  • " + s));
            System.out.println("  ... and " + (liveSubdomains.size() - 15) + " more");
        }
    }
}