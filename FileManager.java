import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;

public class FileManager {
    
    public String createOutputFolder(String domain) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String folderName = domain + "_SubMergeX_" + timestamp;
        
        File folder = new File(folderName);
        if (!folder.exists()) {
            folder.mkdirs();
            System.out.println("Created output folder: " + folderName);
        }
        
        return folderName;
    }
    
    public void saveSubdomains(String outputFolder, String domain, Set<String> subdomains, String type) {
        if (subdomains.isEmpty()) {
            System.out.println("No " + type + " subdomains to save.");
            return;
        }
        
        String filename = outputFolder + "/" + domain + "_" + type + "_subdomains.txt";
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("# " + capitalize(type) + " Subdomains for " + domain);
            writer.println("# Generated on: " + new Date());
            writer.println("# Total subdomains: " + subdomains.size());
            writer.println("# Tool: SubMergeX v2.1");
            writer.println();
            
            subdomains.stream()
                     .sorted()
                     .forEach(writer::println);
            
            System.out.println(capitalize(type) + " subdomains saved to: " + filename);
        } catch (IOException e) {
            System.err.println("Error saving " + type + " subdomains: " + e.getMessage());
        }
    }
    
    public void saveLiveUrls(String outputFolder, String domain, Set<String> liveSubdomains) {
        if (liveSubdomains.isEmpty()) {
            return;
        }
        
        String filename = outputFolder + "/" + domain + "_live_urls.txt";
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("# Live URLs for " + domain);
            writer.println("# Generated on: " + new Date());
            writer.println("# Format: HTTP and HTTPS variants");
            writer.println();
            
            List<String> sortedSubdomains = liveSubdomains.stream()
                .sorted()
                .collect(java.util.stream.Collectors.toList());
                
            for (String subdomain : sortedSubdomains) {
                writer.println("https://" + subdomain);
                writer.println("http://" + subdomain);
            }
            
            System.out.println("Live URLs saved to: " + filename);
        } catch (IOException e) {
            System.err.println("Error saving URLs file: " + e.getMessage());
        }
    }
    
    public Set<String> loadSubdomainsFromFile(String filePath) {
        Set<String> subdomains = new HashSet<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#") && isValidSubdomain(line)) {
                    subdomains.add(line.toLowerCase());
                }
            }
            System.out.println("Loaded " + subdomains.size() + " subdomains from file");
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
        
        return subdomains;
    }
    
    public void generateSummaryReport(String outputFolder, String domain, 
                                    Set<String> allSubdomains, Set<String> liveSubdomains,
                                    List<String> tools, boolean capturedScreenshots) {
        String filename = outputFolder + "/summary_report.txt";
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("SubMergeX Enumeration Summary Report");
            writer.println("=====================================");
            writer.println("Domain: " + domain);
            writer.println("Generated: " + new Date());
            writer.println();
            
            writer.println("STATISTICS:");
            writer.println("-----------");
            if (!tools.isEmpty()) {
                writer.println("Tools used: " + String.join(", ", tools));
                writer.println("Total subdomains discovered: " + allSubdomains.size());
            }
            writer.println("Live subdomains found: " + liveSubdomains.size());
            
            if (allSubdomains.size() > 0) {
                double livePercentage = (double) liveSubdomains.size() / allSubdomains.size() * 100;
                writer.printf("Live subdomain rate: %.1f%%\n", livePercentage);
            }
            
            if (capturedScreenshots) {
                writer.println("Screenshots captured: Yes");
            }
            
            writer.println();
            writer.println("OUTPUT FILES:");
            writer.println("-------------");
            if (!allSubdomains.isEmpty()) {
                writer.println("- " + domain + "_all_subdomains.txt");
            }
            if (!liveSubdomains.isEmpty()) {
                writer.println("- " + domain + "_live_subdomains.txt");
                writer.println("- " + domain + "_live_urls.txt");
            }
            if (capturedScreenshots) {
                writer.println("- screenshots/ (directory)");
                writer.println("- screenshots/report.html");
            }
            
            writer.println();
            writer.println("DISCOVERED SUBDOMAINS:");
            writer.println("----------------------");
            allSubdomains.stream().sorted().forEach(writer::println);
            
            if (!liveSubdomains.isEmpty()) {
                writer.println();
                writer.println("LIVE SUBDOMAINS:");
                writer.println("----------------");
                liveSubdomains.stream().sorted().forEach(writer::println);
            }
            
            System.out.println("Summary report saved to: " + filename);
        } catch (IOException e) {
            System.err.println("Error generating summary report: " + e.getMessage());
        }
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    private boolean isValidSubdomain(String subdomain) {
        return subdomain.matches("^[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}$") &&
               !subdomain.startsWith(".") &&
               !subdomain.endsWith(".") &&
               subdomain.length() > 3;
    }
}