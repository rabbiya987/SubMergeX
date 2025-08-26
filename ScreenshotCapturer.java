import java.io.*;
import java.util.*;

public class ScreenshotCapturer {
    
    public void captureScreenshots(Set<String> liveSubdomains, String outputFolder, Scanner scanner) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("Starting screenshot capture...");
        System.out.println("=".repeat(50));
        
        if (liveSubdomains.isEmpty()) {
            System.out.println("No live subdomains available for screenshots");
            return;
        }
        
        if (!isGoWitnessInstalled()) {
            if (!promptInstallGoWitness(scanner)) {
                System.out.println("Skipping screenshot capture");
                return;
            }
        }
        
        String screenshotDir = outputFolder + "/screenshots";
        createScreenshotDirectory(screenshotDir);
        
        String targetFile = prepareTargetFile(liveSubdomains, screenshotDir);
        if (targetFile != null) {
            executeGoWitness(targetFile, screenshotDir);
            generateReport(screenshotDir);
        }
    }
    
    private boolean isGoWitnessInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", "gowitness");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean promptInstallGoWitness(Scanner scanner) {
        System.out.println("GoWitness not found. This tool is required for screenshot capture.");
        System.out.print("Install GoWitness? (y/n): ");
        String choice = scanner.nextLine().trim().toLowerCase();
        
        if (choice.equals("y") || choice.equals("yes")) {
            return installGoWitness();
        }
        return false;
    }
    
    private boolean installGoWitness() {
        System.out.println("Installing GoWitness...");
        try {
            String installCommand = "go install github.com/sensepost/gowitness@latest";
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", installCommand);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("installed") || line.contains("downloading")) {
                        System.out.println("  " + line);
                    }
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("GoWitness installed successfully!");
                return true;
            } else {
                System.out.println("Failed to install GoWitness. Please install manually:");
                System.out.println("go install github.com/sensepost/gowitness@latest");
                return false;
            }
        } catch (Exception e) {
            System.out.println("Error installing GoWitness: " + e.getMessage());
            return false;
        }
    }
    
    private void createScreenshotDirectory(String screenshotDir) {
        File dir = new File(screenshotDir);
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("Created screenshots directory: " + screenshotDir);
        }
    }
    
    private String prepareTargetFile(Set<String> liveSubdomains, String screenshotDir) {
        String targetFile = screenshotDir + "/target_urls.txt";
        
        try (PrintWriter writer = new PrintWriter(targetFile)) {
            for (String subdomain : liveSubdomains) {
                writer.println("https://" + subdomain);
                writer.println("http://" + subdomain);
            }
            System.out.println("Target URLs file created: " + targetFile);
            System.out.println("Total targets: " + (liveSubdomains.size() * 2) + " URLs");
            return targetFile;
        } catch (IOException e) {
            System.err.println("Error creating target URLs file: " + e.getMessage());
            return null;
        }
    }
    
    private void executeGoWitness(String targetFile, String screenshotDir) {
        String command = String.format(
            "gowitness scan file -f %s --no-http --screenshot-path %s --timeout 10",
            targetFile, screenshotDir
        );
        
        System.out.println("Running GoWitness command:");
        System.out.println("   " + command);
        System.out.println("\nThis may take a while depending on the number of live subdomains...");
        
        try {
            ProcessBuilder builder = new ProcessBuilder("bash", "-c", command);
            Process process = builder.start();
            
            // Monitor progress
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
                 BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                
                int screenshotCount = 0;
                String line;
                
                // Read stdout
                while ((line = reader.readLine()) != null) {
                    if (line.contains("screenshot") || line.contains("Screenshotting")) {
                        screenshotCount++;
                        System.out.printf("Screenshot %d completed\n", screenshotCount);
                    } else if (line.contains("ERROR") || line.contains("WARN")) {
                        System.out.println("  " + line);
                    }
                }
                
                // Read stderr
                while ((line = errorReader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        System.out.println("  " + line);
                    }
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("\nScreenshots captured successfully!");
                System.out.println("Screenshots saved to: " + screenshotDir);
                
                // Count actual screenshot files
                File dir = new File(screenshotDir);
                File[] screenshots = dir.listFiles((d, name) -> 
                    name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg"));
                
                if (screenshots != null) {
                    System.out.println("Total screenshots captured: " + screenshots.length);
                }
            } else {
                System.out.println("GoWitness completed with exit code: " + exitCode);
                System.out.println("Some screenshots may have been captured despite errors.");
            }
            
        } catch (Exception e) {
            System.err.println("Error running GoWitness: " + e.getMessage());
            System.out.println("Make sure GoWitness is properly installed and accessible.");
        }
    }
    
    private void generateReport(String screenshotDir) {
        System.out.println("\nGenerating HTML report...");
        
        try {
            String reportCommand = String.format(
                "gowitness report generate -P %s --sort-perception", screenshotDir);
            ProcessBuilder builder = new ProcessBuilder("bash", "-c", reportCommand);
            Process process = builder.start();
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("HTML report generated: " + screenshotDir + "/report.html");
                System.out.println("Open the report in your browser to view all screenshots.");
            } else {
                System.out.println("Could not generate HTML report (exit code: " + exitCode + ")");
                System.out.println("Screenshots are still available in: " + screenshotDir);
            }
        } catch (Exception e) {
            System.out.println("Could not generate HTML report: " + e.getMessage());
            System.out.println("Screenshots are still available in: " + screenshotDir);
        }
    }
    
    public void displayScreenshotSummary(String outputFolder, int totalTargets) {
        String screenshotDir = outputFolder + "/screenshots";
        File dir = new File(screenshotDir);
        
        if (!dir.exists()) {
            return;
        }
        
        File[] screenshots = dir.listFiles((d, name) -> 
            name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg"));
        
        System.out.println("\n" + "=".repeat(50));
        System.out.println("SCREENSHOT CAPTURE COMPLETE");
        System.out.println("=".repeat(50));
        System.out.println("Screenshots directory: " + screenshotDir);
        
        if (screenshots != null) {
            System.out.println("Screenshots captured: " + screenshots.length + "/" + totalTargets);
            double successRate = (double) screenshots.length / totalTargets * 100;
            System.out.printf("Success rate: %.1f%%\n", successRate);
        }
        
        File reportFile = new File(screenshotDir + "/report.html");
        if (reportFile.exists()) {
            System.out.println("HTML Report: " + reportFile.getAbsolutePath());
        }
    }
}