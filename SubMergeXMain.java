import java.util.*;

public class SubMergeXMain {
    private SubdomainFinder subdomainFinder;
    private LiveChecker liveChecker;
    private ScreenshotCapturer screenshotCapturer;
    private FileManager fileManager;
    
    public SubMergeXMain() {
        this.subdomainFinder = new SubdomainFinder();
        this.liveChecker = new LiveChecker();
        this.screenshotCapturer = new ScreenshotCapturer();
        this.fileManager = new FileManager();
    }
    
    public static void main(String[] args) {
        SubMergeXMain app = new SubMergeXMain();
        app.run();
    }
    
    public void run() {
        printHeader();
        Scanner scanner = new Scanner(System.in);

        try {
            String domain = getDomain(scanner);
            
            // Display main menu and get user choices
            EnumerationConfig config = displayMainMenu(scanner);
            
            if (!config.findSubdomains && !config.checkLiveSubdomains && !config.captureScreenshots) {
                System.out.println("No options selected. Exiting...");
                return;
            }
            
            Set<String> allSubdomains = new HashSet<>();
            Set<String> liveSubdomains = new HashSet<>();
            String outputFolder = "";
            List<String> selectedTools = new ArrayList<>();
            
            System.out.println("\n" + "=".repeat(50));
            System.out.println("Starting SubMergeX operations...");
            System.out.println("=".repeat(50));
            
            // Step 1: Find subdomains (if selected)
            if (config.findSubdomains) {
                selectedTools = subdomainFinder.selectTools(scanner);
                allSubdomains = subdomainFinder.findSubdomains(domain, selectedTools, scanner);
                
                if (!allSubdomains.isEmpty()) {
                    outputFolder = fileManager.createOutputFolder(domain);
                    fileManager.saveSubdomains(outputFolder, domain, allSubdomains, "all");
                } else {
                    System.out.println("No subdomains found. Check your tools and commands.");
                    if (!config.checkLiveSubdomains && !config.captureScreenshots) {
                        return;
                    }
                }
            }
            
            // Step 2: Check live subdomains (if selected)
            if (config.checkLiveSubdomains) {
                if (allSubdomains.isEmpty()) {
                    allSubdomains = handleMissingSubdomains(scanner, domain);
                    if (allSubdomains.isEmpty()) {
                        if (!config.captureScreenshots) return;
                    }
                }
                
                if (outputFolder.isEmpty()) {
                    outputFolder = fileManager.createOutputFolder(domain);
                }
                
                if (!allSubdomains.isEmpty()) {
                    liveSubdomains = liveChecker.checkLiveSubdomains(allSubdomains, scanner, outputFolder);
                    if (!liveSubdomains.isEmpty()) {
                        fileManager.saveSubdomains(outputFolder, domain, liveSubdomains, "live");
                        fileManager.saveLiveUrls(outputFolder, domain, liveSubdomains);
                    }
                }
            }
            
            // Step 3: Capture screenshots (if selected)
            if (config.captureScreenshots) {
                if (liveSubdomains.isEmpty()) {
                    liveSubdomains = handleMissingLiveSubdomains(scanner, domain, allSubdomains, outputFolder);
                }
                
                if (!liveSubdomains.isEmpty()) {
                    if (outputFolder.isEmpty()) {
                        outputFolder = fileManager.createOutputFolder(domain);
                    }
                    screenshotCapturer.captureScreenshots(liveSubdomains, outputFolder, scanner);
                    screenshotCapturer.displayScreenshotSummary(outputFolder, liveSubdomains.size() * 2);
                } else {
                    System.out.println("No live subdomains available for screenshots");
                }
            }
            
            // Generate final summary
            if (!outputFolder.isEmpty()) {
                fileManager.generateSummaryReport(outputFolder, domain, allSubdomains, 
                                                liveSubdomains, selectedTools, config.captureScreenshots);
            }
            
            // Display final summary
            displayFinalSummary(domain, allSubdomains, liveSubdomains, selectedTools, config, outputFolder);
            
        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    private void printHeader() {
        System.out.println("===============================================================");
        System.out.println("                   SubMergeX                                   ");
        System.out.println("===============================================================");
        System.out.println("Description:");
        System.out.println("Enhanced subdomain collector with modular architecture,");
        System.out.println("menu-driven interface, and advanced features.");
        System.out.println("\nAuthor: Rabia Ishtiaq");
        System.out.println("GitHub: https://github.com/rabbiya987/SubMergeX");
        System.out.println("Date: 2025-08-26");
        System.out.println("===============================================================\n");
    }
    
    private String getDomain(Scanner scanner) {
        String domain;
        do {
            System.out.print("Enter the target domain: ");
            domain = scanner.nextLine().trim();
            if (domain.isEmpty()) {
                System.out.println("Domain cannot be empty. Please try again.");
            } else if (!isValidDomain(domain)) {
                System.out.println("Invalid domain format. Please enter a valid domain.");
                domain = "";
            }
        } while (domain.isEmpty());
        return domain;
    }

    private boolean isValidDomain(String domain) {
        return domain.matches("^[a-zA-Z0-9][a-zA-Z0-9-]{0,61}[a-zA-Z0-9]?\\.[a-zA-Z]{2,}$");
    }
    
    private EnumerationConfig displayMainMenu(Scanner scanner) {
        EnumerationConfig config = new EnumerationConfig();
        
        System.out.println("\nMAIN MENU - What would you like to do?");
        System.out.println("=".repeat(45));
        System.out.println("1. Find Subdomains");
        System.out.println("2. Check Live Subdomains");
        System.out.println("3. Capture Screenshots");
        System.out.println("4. Complete Workflow");
        System.out.println("5. Custom Combination");
        
        System.out.println("\nQuick Options:");
        System.out.println("• Enter numbers separated by commas (e.g., 1,2)");
        
        boolean validChoice = false;
        while (!validChoice) {
            System.out.print("\nYour choice: ");
            String input = scanner.nextLine().trim();
            
            try {
                if (input.contains(",")) {
                    String[] choices = input.split(",");
                    for (String choice : choices) {
                        int num = Integer.parseInt(choice.trim());
                        switch (num) {
                            case 1:
                                config.findSubdomains = true;
                                break;
                            case 2:
                                config.checkLiveSubdomains = true;
                                break;
                            case 3:
                                config.captureScreenshots = true;
                                break;
                            default:
                                throw new IllegalArgumentException("Invalid choice: " + num);
                        }
                    }
                    validChoice = true;
                } else {
                    int choice = Integer.parseInt(input);
                    switch (choice) {
                        case 1:
                            config.findSubdomains = true;
                            validChoice = true;
                            break;
                        case 2:
                            config.checkLiveSubdomains = true;
                            validChoice = true;
                            break;
                        case 3:
                            config.captureScreenshots = true;
                            validChoice = true;
                            break;
                        case 4:
                            config.findSubdomains = true;
                            config.checkLiveSubdomains = true;
                            config.captureScreenshots = true;
                            validChoice = true;
                            break;
                        case 5:
                            config = displayCustomMenu(scanner);
                            validChoice = true;
                            break;
                        default:
                            System.out.println("Invalid choice. Please select 1-5.");
                    }
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter numbers only.");
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            }
        }
        
        // Display selected operations
        System.out.println("\nSelected operations:");
        if (config.findSubdomains) System.out.println("  • Subdomain enumeration");
        if (config.checkLiveSubdomains) System.out.println("  • Live subdomain checking");
        if (config.captureScreenshots) System.out.println("  • Screenshot capture");
        
        return config;
    }
    
    private EnumerationConfig displayCustomMenu(Scanner scanner) {
        EnumerationConfig config = new EnumerationConfig();
        
        System.out.println("\nCUSTOM CONFIGURATION");
        System.out.println("=".repeat(30));
        
        System.out.print("Find subdomains? (y/n): ");
        String findChoice = scanner.nextLine().trim().toLowerCase();
        config.findSubdomains = findChoice.equals("y") || findChoice.equals("yes");
        
        System.out.print("Check live subdomains? (y/n): ");
        String liveChoice = scanner.nextLine().trim().toLowerCase();
        config.checkLiveSubdomains = liveChoice.equals("y") || liveChoice.equals("yes");
        
        System.out.print("Capture screenshots? (y/n): ");
        String screenshotChoice = scanner.nextLine().trim().toLowerCase();
        config.captureScreenshots = screenshotChoice.equals("y") || screenshotChoice.equals("yes");
        
        return config;
    }
    
    private Set<String> handleMissingSubdomains(Scanner scanner, String domain) {
        System.out.print("\nEnter path to subdomain list file (or press Enter to enumerate first): ");
        String filePath = scanner.nextLine().trim();
        
        if (filePath.isEmpty()) {
            System.out.println("Need to find subdomains first...");
            List<String> selectedTools = subdomainFinder.selectTools(scanner);
            return subdomainFinder.findSubdomains(domain, selectedTools, scanner);
        } else {
            Set<String> loadedSubdomains = fileManager.loadSubdomainsFromFile(filePath);
            if (loadedSubdomains.isEmpty()) {
                System.out.println("Could not load subdomains from file: " + filePath);
            }
            return loadedSubdomains;
        }
    }
    
    private Set<String> handleMissingLiveSubdomains(Scanner scanner, String domain, 
                                                   Set<String> allSubdomains, String outputFolder) {
        if (allSubdomains.isEmpty()) {
            System.out.print("\nEnter path to live subdomain list file (or press Enter to find and check first): ");
            String filePath = scanner.nextLine().trim();
            
            if (filePath.isEmpty()) {
                System.out.println("Need to find and check subdomains first...");
                List<String> selectedTools = subdomainFinder.selectTools(scanner);
                allSubdomains = subdomainFinder.findSubdomains(domain, selectedTools, scanner);
                
                if (allSubdomains.isEmpty()) {
                    System.out.println("No subdomains found. Cannot proceed.");
                    return new HashSet<>();
                }
                
                fileManager.saveSubdomains(outputFolder, domain, allSubdomains, "all");
                return liveChecker.checkLiveSubdomains(allSubdomains, scanner, outputFolder);
            } else {
                return fileManager.loadSubdomainsFromFile(filePath);
            }
        } else {
            System.out.println("You have subdomains but haven't checked which are live.");
            System.out.print("Do you want to use all subdomains for screenshots? (y/n): ");
            String useAll = scanner.nextLine().trim().toLowerCase();
            
            if (useAll.equals("y") || useAll.equals("yes")) {
                return allSubdomains;
            } else {
                System.out.println("Checking live subdomains first...");
                return liveChecker.checkLiveSubdomains(allSubdomains, scanner, outputFolder);
            }
        }
    }
    
    private void displayFinalSummary(String domain, Set<String> allSubdomains, Set<String> liveSubdomains, 
                                   List<String> tools, EnumerationConfig config, String outputFolder) {
        System.out.println("\n" + "=".repeat(65));
        System.out.println("FINAL ENUMERATION SUMMARY");
        System.out.println("=".repeat(65));
        
        System.out.println("Domain: " + domain);
        System.out.println("STATISTICS:");
        
        if (config.findSubdomains && !tools.isEmpty()) {
            System.out.println("  Tools used: " + String.join(", ", tools));
            System.out.println("  Total subdomains discovered: " + allSubdomains.size());
        }
        
        if (config.checkLiveSubdomains) {
            System.out.println("  Live subdomains found: " + liveSubdomains.size());
            if (allSubdomains.size() > 0) {
                double livePercentage = (double) liveSubdomains.size() / allSubdomains.size() * 100;
                System.out.printf("  Success rate: %.1f%%\n", livePercentage);
            }
        }
        
        if (config.captureScreenshots) {
            System.out.println("  Screenshots captured: " + liveSubdomains.size() + " targets");
        }
        
        if (!outputFolder.isEmpty()) {
            System.out.println("\nOUTPUT FILES:");
            System.out.println("  Main folder: " + outputFolder);
            
            if (config.findSubdomains && !allSubdomains.isEmpty()) {
                System.out.println("  All subdomains: " + domain + "_all_subdomains.txt");
            }
            
            if (config.checkLiveSubdomains && !liveSubdomains.isEmpty()) {
                System.out.println("  Live subdomains: " + domain + "_live_subdomains.txt");
                System.out.println("  Live URLs: " + domain + "_live_urls.txt");
            }
            
            if (config.captureScreenshots) {
                System.out.println("  Screenshots: screenshots/");
                System.out.println("  HTML Report: screenshots/report.html");
            }
            
            System.out.println("  Summary report: summary_report.txt");
        }
        
        System.out.println("\n" + "=".repeat(65));
        System.out.println("SUBMERGEX OPERATIONS COMPLETED SUCCESSFULLY!");
        System.out.println("=".repeat(65));
    }
    
    private static class EnumerationConfig {
        boolean findSubdomains = false;
        boolean checkLiveSubdomains = false;
        boolean captureScreenshots = false;
    }
}