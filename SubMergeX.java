import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;

public class SubMergeX {
    private static final Map<String, String> DEFAULT_COMMANDS = new HashMap<>();
    
    static {
        DEFAULT_COMMANDS.put("subfinder", "subfinder -d %s -silent");
        DEFAULT_COMMANDS.put("assetfinder", "assetfinder --subs-only %s");
        DEFAULT_COMMANDS.put("amass", "amass enum -passive -d %s");
        DEFAULT_COMMANDS.put("findomain", "findomain -t %s -q");
        DEFAULT_COMMANDS.put("crt.sh", "curl -s 'https://crt.sh/?q=%%25.%s&output=json' | jq -r '.[].name_value' | sort -u");
        DEFAULT_COMMANDS.put("sublist3r", "sublist3r -d %s -o /dev/stdout");
    }
    
    // Live checking tools
    private static final Map<String, String> LIVE_CHECK_COMMANDS = new HashMap<>();
    
    static {
        LIVE_CHECK_COMMANDS.put("httprobe", "httprobe -c 50");
        LIVE_CHECK_COMMANDS.put("httpx", "httpx -silent -threads 100");
        LIVE_CHECK_COMMANDS.put("custom", ""); // User defined
    }
    
    private static class EnumerationConfig {
        boolean findSubdomains = false;
        boolean checkLiveSubdomains = false;
        boolean captureScreenshots = false;
        String screenshotOutputDir = "";
        String liveCheckTool = "httprobe";
        String liveCheckCommand = "";
        int connectionTimeout = 5000; // 5 seconds
        int maxThreads = 50;
    }

    public static void main(String[] args) {
        printHeader();
        Scanner scanner = new Scanner(System.in);

        try {
            String domain = getDomain(scanner);
            
            // Check and install tools
            checkAndInstallTools(scanner);
            
            // Display main menu and get user choices
            EnumerationConfig config = displayMainMenu(scanner);
            
            if (!config.findSubdomains && !config.checkLiveSubdomains && !config.captureScreenshots) {
                System.out.println("❌ No options selected. Exiting...");
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
                selectedTools = selectTools(scanner);
                List<String> commands = configureCommands(scanner, selectedTools, domain);
                allSubdomains = executeCommands(commands);
                
                if (!allSubdomains.isEmpty()) {
                    outputFolder = createOutputFolder(domain);
                    saveInitialResults(outputFolder, domain, allSubdomains);
                    displayInitialSummary(allSubdomains, selectedTools);
                } else {
                    System.out.println("❌ No subdomains found. Check your tools and commands.");
                    return;
                }
            }
            
            // Step 2: Check live subdomains (if selected)
            if (config.checkLiveSubdomains) {
                if (allSubdomains.isEmpty()) {
                    // User wants to check live subdomains but didn't enumerate first
                    System.out.print("\n📝 Enter path to subdomain list file (or press Enter to enumerate first): ");
                    String filePath = scanner.nextLine().trim();
                    
                    if (filePath.isEmpty()) {
                        System.out.println("📋 Need to find subdomains first...");
                        selectedTools = selectTools(scanner);
                        List<String> commands = configureCommands(scanner, selectedTools, domain);
                        allSubdomains = executeCommands(commands);
                        
                        if (!allSubdomains.isEmpty()) {
                            outputFolder = createOutputFolder(domain);
                            saveInitialResults(outputFolder, domain, allSubdomains);
                            displayInitialSummary(allSubdomains, selectedTools);
                        } else {
                            System.out.println("❌ No subdomains found. Cannot proceed with live checking.");
                            return;
                        }
                    } else {
                        // Load subdomains from file
                        allSubdomains = loadSubdomainsFromFile(filePath);
                        if (allSubdomains.isEmpty()) {
                            System.out.println("❌ Could not load subdomains from file: " + filePath);
                            return;
                        }
                        outputFolder = createOutputFolder(domain);
                        System.out.println("✅ Loaded " + allSubdomains.size() + " subdomains from file");
                    }
                }
                
                if (outputFolder.isEmpty()) {
                    outputFolder = createOutputFolder(domain);
                }
                
                config = configureLiveChecking(scanner, outputFolder, config);
                if (config.checkLiveSubdomains) {
                    liveSubdomains = checkLiveSubdomainsWithTool(allSubdomains, config, outputFolder);
                    saveLiveResults(outputFolder, domain, liveSubdomains);
                }
            }
            
            // Step 3: Capture screenshots (if selected)
            if (config.captureScreenshots) {
                if (liveSubdomains.isEmpty()) {
                    if (allSubdomains.isEmpty()) {
                        // User wants screenshots but has no subdomains
                        System.out.print("\n📝 Enter path to live subdomain list file (or press Enter to find and check first): ");
                        String filePath = scanner.nextLine().trim();
                        
                        if (filePath.isEmpty()) {
                            System.out.println("📋 Need to find and check subdomains first...");
                            
                            // Find subdomains
                            selectedTools = selectTools(scanner);
                            List<String> commands = configureCommands(scanner, selectedTools, domain);
                            allSubdomains = executeCommands(commands);
                            
                            if (allSubdomains.isEmpty()) {
                                System.out.println("❌ No subdomains found. Cannot proceed.");
                                return;
                            }
                            
                            outputFolder = createOutputFolder(domain);
                            saveInitialResults(outputFolder, domain, allSubdomains);
                            
                            // Check live subdomains
                            config.checkLiveSubdomains = true;
                            config = configureLiveChecking(scanner, outputFolder, config);
                            liveSubdomains = checkLiveSubdomainsWithTool(allSubdomains, config, outputFolder);
                            saveLiveResults(outputFolder, domain, liveSubdomains);
                        } else {
                            // Load live subdomains from file
                            liveSubdomains = loadSubdomainsFromFile(filePath);
                            if (liveSubdomains.isEmpty()) {
                                System.out.println("❌ Could not load subdomains from file: " + filePath);
                                return;
                            }
                            outputFolder = createOutputFolder(domain);
                            System.out.println("✅ Loaded " + liveSubdomains.size() + " live subdomains from file");
                        }
                    } else if (!config.checkLiveSubdomains) {
                        // User has subdomains but didn't check for live ones
                        System.out.println("⚠️  You have subdomains but haven't checked which are live.");
                        System.out.print("Do you want to use all subdomains for screenshots? (y/n): ");
                        String useAll = scanner.nextLine().trim().toLowerCase();
                        
                        if (useAll.equals("y") || useAll.equals("yes")) {
                            liveSubdomains = allSubdomains;
                        } else {
                            System.out.println("📋 Checking live subdomains first...");
                            config.checkLiveSubdomains = true;
                            config = configureLiveChecking(scanner, outputFolder, config);
                            liveSubdomains = checkLiveSubdomainsWithTool(allSubdomains, config, outputFolder);
                            saveLiveResults(outputFolder, domain, liveSubdomains);
                        }
                    }
                }
                
                if (!liveSubdomains.isEmpty()) {
                    if (outputFolder.isEmpty()) {
                        outputFolder = createOutputFolder(domain);
                    }
                    
                    if (isToolInstalled("gowitness")) {
                        config.screenshotOutputDir = outputFolder + "/screenshots";
                        captureScreenshots(liveSubdomains, config);
                    } else {
                        System.out.print("❌ GoWitness not found. Install it? (y/n): ");
                        String installChoice = scanner.nextLine().trim().toLowerCase();
                        if (installChoice.equals("y") || installChoice.equals("yes")) {
                            installGoWitness();
                            config.screenshotOutputDir = outputFolder + "/screenshots";
                            captureScreenshots(liveSubdomains, config);
                        } else {
                            System.out.println("⏭️  Skipping screenshot capture");
                        }
                    }
                } else {
                    System.out.println("❌ No live subdomains available for screenshots");
                }
            }
            
            // Display final summary
            displayFinalSummary(allSubdomains, liveSubdomains, selectedTools, config, outputFolder);
            
        } catch (Exception e) {
            System.err.println("❌ An error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    private static void printHeader() {
        System.out.println("===============================================================");
        System.out.println("                   🔍 SubMergeX v2.1 - Advanced Edition");
        System.out.println("===============================================================");
        System.out.println("Description:");
        System.out.println("Enhanced subdomain collector with menu-driven interface,");
        System.out.println("default commands, parallel execution, and advanced features.");
        System.out.println("\n✨ New Features:");
        System.out.println(" • Interactive main menu for operation selection");
        System.out.println(" • Flexible workflow - choose what you need");
        System.out.println(" • Pre-configured default commands");
        System.out.println(" • Live subdomain checking with httprobe/httpx");
        System.out.println(" • GoWitness screenshot integration");
        System.out.println(" • Automatic tool installation");
        System.out.println(" • Progress tracking and statistics");
        System.out.println(" • Better error handling and validation");
        System.out.println(" • Multiple output formats");
        System.out.println("\nAuthor: Rabia Ishtiaq");
        System.out.println("GitHub: https://github.com/rabbiya987/SubMergeX");
        System.out.println("Date: 2025-08-26");
        System.out.println("===============================================================\n");
    }
    
    private static EnumerationConfig displayMainMenu(Scanner scanner) {
        EnumerationConfig config = new EnumerationConfig();
        
        System.out.println("\n🎯 MAIN MENU - What would you like to do?");
        System.out.println("=".repeat(45));
        System.out.println("1. 🔍 Find Subdomains");
        System.out.println("   └─ Enumerate subdomains using multiple tools");
        System.out.println("2. ✅ Check Live Subdomains");
        System.out.println("   └─ Verify which subdomains are accessible");
        System.out.println("3. 📸 Capture Screenshots");
        System.out.println("   └─ Take screenshots of live subdomains");
        System.out.println("4. 🚀 Complete Workflow");
        System.out.println("   └─ All of the above (recommended)");
        System.out.println("5. 🔧 Custom Combination");
        System.out.println("   └─ Choose specific operations");
        
        System.out.println("\n📝 Quick Options:");
        System.out.println("• Enter numbers separated by commas (e.g., 1,2)");
        System.out.println("• Or select a preset option (1-5)");
        
        boolean validChoice = false;
        while (!validChoice) {
            System.out.print("\n🎮 Your choice: ");
            String input = scanner.nextLine().trim();
            
            try {
                if (input.contains(",")) {
                    // Multiple selections
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
                            System.out.println("❌ Invalid choice. Please select 1-5.");
                    }
                }
            } catch (NumberFormatException e) {
                System.out.println("❌ Invalid input. Please enter numbers only.");
            } catch (IllegalArgumentException e) {
                System.out.println("❌ " + e.getMessage());
            }
        }
        
        // Display selected operations
        System.out.println("\n✅ Selected operations:");
        if (config.findSubdomains) System.out.println("  🔍 Subdomain enumeration");
        if (config.checkLiveSubdomains) System.out.println("  ✅ Live subdomain checking");
        if (config.captureScreenshots) System.out.println("  📸 Screenshot capture");
        
        return config;
    }
    
    private static EnumerationConfig displayCustomMenu(Scanner scanner) {
        EnumerationConfig config = new EnumerationConfig();
        
        System.out.println("\n🔧 CUSTOM CONFIGURATION");
        System.out.println("=".repeat(30));
        
        System.out.print("🔍 Find subdomains? (y/n): ");
        String findChoice = scanner.nextLine().trim().toLowerCase();
        config.findSubdomains = findChoice.equals("y") || findChoice.equals("yes");
        
        System.out.print("✅ Check live subdomains? (y/n): ");
        String liveChoice = scanner.nextLine().trim().toLowerCase();
        config.checkLiveSubdomains = liveChoice.equals("y") || liveChoice.equals("yes");
        
        System.out.print("📸 Capture screenshots? (y/n): ");
        String screenshotChoice = scanner.nextLine().trim().toLowerCase();
        config.captureScreenshots = screenshotChoice.equals("y") || screenshotChoice.equals("yes");
        
        return config;
    }
    
    private static Set<String> loadSubdomainsFromFile(String filePath) {
        Set<String> subdomains = new HashSet<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#") && isValidSubdomain(line)) {
                    subdomains.add(line.toLowerCase());
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Error reading file: " + e.getMessage());
        }
        
        return subdomains;
    }

    private static String getDomain(Scanner scanner) {
        String domain;
        do {
            System.out.print("🌐 Enter the target domain: ");
            domain = scanner.nextLine().trim();
            if (domain.isEmpty()) {
                System.out.println("❌ Domain cannot be empty. Please try again.");
            } else if (!isValidDomain(domain)) {
                System.out.println("❌ Invalid domain format. Please enter a valid domain.");
                domain = "";
            }
        } while (domain.isEmpty());
        return domain;
    }

    private static boolean isValidDomain(String domain) {
        return domain.matches("^[a-zA-Z0-9][a-zA-Z0-9-]{0,61}[a-zA-Z0-9]?\\.[a-zA-Z]{2,}$");
    }

    private static void checkAndInstallTools(Scanner scanner) {
        System.out.println("\n🔧 Checking required tools...");
        System.out.println("=".repeat(35));
        
        // Essential tools check
        Map<String, String> toolsToCheck = new HashMap<>();
        toolsToCheck.put("subfinder", "go install -v github.com/projectdiscovery/subfinder/v2/cmd/subfinder@latest");
        toolsToCheck.put("assetfinder", "go install github.com/tomnomnom/assetfinder@latest");
        toolsToCheck.put("amass", "go install -v github.com/owasp-amass/amass/v4/...@master");
        toolsToCheck.put("findomain", "Installation guide: https://github.com/Findomain/Findomain/blob/master/docs/INSTALLATION.md");
        toolsToCheck.put("httprobe", "go install github.com/tomnomnom/httprobe@latest");
        toolsToCheck.put("gowitness", "go install github.com/sensepost/gowitness@latest");
        
        List<String> missingTools = new ArrayList<>();
        
        for (String tool : toolsToCheck.keySet()) {
            if (isToolInstalled(tool)) {
                System.out.println("✅ " + tool + " - Found");
            } else {
                System.out.println("❌ " + tool + " - Not found");
                missingTools.add(tool);
            }
        }
        
        if (!missingTools.isEmpty()) {
            System.out.println("\n⚠️  Missing tools detected: " + String.join(", ", missingTools));
            System.out.print("🔧 Do you want to install missing tools automatically? (y/n): ");
            String installChoice = scanner.nextLine().trim().toLowerCase();
            
            if (installChoice.equals("y") || installChoice.equals("yes")) {
                for (String tool : missingTools) {
                    if (!tool.equals("findomain")) { // Special case for findomain
                        installTool(tool, toolsToCheck.get(tool));
                    } else {
                        System.out.println("💡 Please install findomain manually: " + toolsToCheck.get(tool));
                    }
                }
            } else {
                System.out.println("⚠️  You can continue, but some features may not work without these tools.");
            }
        } else {
            System.out.println("\n🎉 All tools are installed and ready!");
        }
    }
    
    private static boolean isToolInstalled(String toolName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", toolName);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private static void installTool(String toolName, String installCommand) {
        System.out.println("📥 Installing " + toolName + "...");
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", installCommand);
            Process process = pb.start();
            
            // Show some output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("installed") || line.contains("downloading")) {
                        System.out.println("  📦 " + line);
                    }
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("✅ " + toolName + " installed successfully!");
            } else {
                System.out.println("❌ Failed to install " + toolName + ". Please install manually.");
            }
        } catch (Exception e) {
            System.out.println("❌ Error installing " + toolName + ": " + e.getMessage());
        }
    }
    
    private static void installGoWitness() {
        System.out.println("📥 Installing GoWitness...");
        installTool("gowitness", "go install github.com/sensepost/gowitness@latest");
    }
    
    private static String createOutputFolder(String domain) {
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String folderName = domain + "_SubMergeX_" + timestamp;
        
        File folder = new File(folderName);
        if (!folder.exists()) {
            folder.mkdirs();
            System.out.println("\n📁 Created output folder: " + folderName);
        }
        
        return folderName;
    }
    
    private static void saveInitialResults(String outputFolder, String domain, Set<String> subdomains) {
        String filename = outputFolder + "/" + domain + "_all_subdomains.txt";
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Write header
            writer.println("# All Subdomains for " + domain);
            writer.println("# Generated on: " + new Date());
            writer.println("# Total subdomains found: " + subdomains.size());
            writer.println("# Tool: SubMergeX v2.1");
            writer.println("# Status: Initial enumeration complete");
            writer.println();
            
            // Write sorted subdomains
            subdomains.stream()
                     .sorted()
                     .forEach(writer::println);
            
            System.out.println("💾 All subdomains saved to: " + filename);
        } catch (IOException e) {
            System.err.println("❌ Error writing initial results: " + e.getMessage());
        }
    }
    
    private static void displayInitialSummary(Set<String> subdomains, List<String> tools) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("📊 INITIAL ENUMERATION COMPLETE");
        System.out.println("=".repeat(50));
        System.out.println("🎯 Tools used: " + String.join(", ", tools));
        System.out.println("🔍 Total unique subdomains found: " + subdomains.size());
        
        if (subdomains.size() <= 15) {
            System.out.println("\n📋 All discovered subdomains:");
            subdomains.stream().sorted().forEach(s -> System.out.println("  • " + s));
        } else {
            System.out.println("\n📋 Sample subdomains (first 15):");
            subdomains.stream().sorted().limit(15)
                     .forEach(s -> System.out.println("  • " + s));
            System.out.println("  ... and " + (subdomains.size() - 15) + " more");
        }
    }
    
    private static EnumerationConfig configureLiveChecking(Scanner scanner, String outputFolder, EnumerationConfig config) {
        config.checkLiveSubdomains = true;
        
        System.out.println("\n⚙️ Live Subdomain Checking Configuration:");
        System.out.println("=".repeat(45));
        
        // Select live checking tool
        System.out.println("🛠️  Available live checking tools:");
        System.out.println("1. httprobe (recommended) - Fast HTTP probe");
        System.out.println("2. httpx - Feature-rich HTTP toolkit");
        System.out.println("3. custom - Define your own command");
        
        System.out.print("Select tool (1-3, default: httprobe): ");
        String toolChoice = scanner.nextLine().trim();
        
        switch (toolChoice) {
            case "2":
                if (isToolInstalled("httpx")) {
                    config.liveCheckTool = "httpx";
                    config.liveCheckCommand = LIVE_CHECK_COMMANDS.get("httpx");
                } else {
                    System.out.println("❌ httpx not found, using httprobe");
                    config.liveCheckTool = "httprobe";
                    config.liveCheckCommand = LIVE_CHECK_COMMANDS.get("httprobe");
                }
                break;
            case "3":
                config.liveCheckTool = "custom";
                System.out.print("Enter custom command (subdomains will be piped to it): ");
                config.liveCheckCommand = scanner.nextLine().trim();
                break;
            default:
                config.liveCheckTool = "httprobe";
                config.liveCheckCommand = LIVE_CHECK_COMMANDS.get("httprobe");
                break;
        }
        
        // Configure the selected tool
        System.out.println("\n🔧 Tool: " + config.liveCheckTool);
        System.out.println("📝 Default command: " + config.liveCheckCommand);
        System.out.print("Use default command? (y/n/modify): ");
        String commandChoice = scanner.nextLine().trim().toLowerCase();
        
        switch (commandChoice) {
            case "n":
            case "no":
                System.out.println("⏭️  Skipping live checking");
                config.checkLiveSubdomains = false;
                return config;
            case "modify":
            case "m":
                System.out.print("Enter modified command: ");
                String modifiedCommand = scanner.nextLine().trim();
                if (!modifiedCommand.isEmpty()) {
                    config.liveCheckCommand = modifiedCommand;
                }
                break;
            default:
                // Keep default command
                break;
        }
        
        return config;
    }
    
    private static Set<String> checkLiveSubdomainsWithTool(Set<String> subdomains, EnumerationConfig config, String outputFolder) {
        System.out.println("\n🔍 Checking live subdomains with " + config.liveCheckTool + "...");
        System.out.println("=".repeat(50));
        
        Set<String> liveSubdomains = new HashSet<>();
        
        try {
            // Create temporary file with subdomains
            String tempFile = outputFolder + "/temp_subdomains.txt";
            try (PrintWriter writer = new PrintWriter(tempFile)) {
                for (String subdomain : subdomains) {
                    writer.println(subdomain);
                }
            }
            
            // Build the live checking command
            String fullCommand = "cat " + tempFile + " | " + config.liveCheckCommand;
            System.out.println("🚀 Running: " + fullCommand);
            
            long startTime = System.currentTimeMillis();
            
            ProcessBuilder builder = new ProcessBuilder("bash", "-c", fullCommand);
            Process process = builder.start();
            
            // Read the output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        // Extract domain from URL (remove http/https prefix)
                        String domain = line.replaceAll("^https?://", "").split("/")[0];
                        liveSubdomains.add(domain);
                        System.out.println("✅ Live: " + line);
                    }
                }
            }
            
            process.waitFor();
            long endTime = System.currentTimeMillis();
            
            // Clean up temp file
            new File(tempFile).delete();
            
            System.out.printf("\n📊 Live check completed in %.2fs\n", (endTime - startTime) / 1000.0);
            System.out.printf("🎯 Found %d/%d live subdomains (%.1f%%)\n", 
                            liveSubdomains.size(), subdomains.size(),
                            (double) liveSubdomains.size() / subdomains.size() * 100);
            
        } catch (Exception e) {
            System.err.println("❌ Error during live checking: " + e.getMessage());
            System.out.println("💡 Falling back to Java-based live checking...");
            return checkLiveSubdomains(subdomains, config);
        }
        
        return liveSubdomains;
    }
    
    private static void saveLiveResults(String outputFolder, String domain, Set<String> liveSubdomains) {
        if (liveSubdomains.isEmpty()) {
            System.out.println("ℹ️  No live subdomains to save.");
            return;
        }
        
        // Save live subdomains
        String liveFile = outputFolder + "/" + domain + "_live_subdomains.txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(liveFile))) {
            writer.println("# Live Subdomains for " + domain);
            writer.println("# Generated on: " + new Date());
            writer.println("# Total live subdomains: " + liveSubdomains.size());
            writer.println("# Tool: SubMergeX v2.1");
            writer.println();
            
            liveSubdomains.stream()
                         .sorted()
                         .forEach(writer::println);
            
            System.out.println("💾 Live subdomains saved to: " + liveFile);
        } catch (IOException e) {
            System.err.println("❌ Error saving live results: " + e.getMessage());
        }
        
        // Save URLs for other tools
        String urlsFile = outputFolder + "/" + domain + "_live_urls.txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(urlsFile))) {
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
            
            System.out.println("🌐 Live URLs saved to: " + urlsFile);
        } catch (IOException e) {
            System.err.println("❌ Error saving URLs file: " + e.getMessage());
        }
    }

    private static List<String> selectTools(Scanner scanner) {
        System.out.println("\n🛠️  Available Subdomain Enumeration Tools:");
        System.out.println("=".repeat(45));
        
        List<String> availableTools = new ArrayList<>(DEFAULT_COMMANDS.keySet());
        for (int i = 0; i < availableTools.size(); i++) {
            System.out.printf("%d. %s\n", i + 1, availableTools.get(i));
        }
        
        System.out.println("\n📋 Selection Options:");
        System.out.println("• Enter numbers separated by commas (e.g., 1,2,3)");
        System.out.println("• Enter 'all' to select all tools");
        System.out.println("• Enter 'recommended' for subfinder, assetfinder, amass");

        List<String> selectedTools = new ArrayList<>();
        boolean validSelection = false;

        while (!validSelection) {
            System.out.print("\n🔢 Your selection: ");
            String input = scanner.nextLine().trim().toLowerCase();

            if (input.equals("all")) {
                selectedTools = new ArrayList<>(availableTools);
                validSelection = true;
            } else if (input.equals("recommended")) {
                selectedTools = Arrays.asList("subfinder", "assetfinder", "amass");
                validSelection = true;
            } else {
                try {
                    String[] selections = input.split(",");
                    Set<String> uniqueTools = new HashSet<>();
                    
                    for (String sel : selections) {
                        int index = Integer.parseInt(sel.trim()) - 1;
                        if (index >= 0 && index < availableTools.size()) {
                            uniqueTools.add(availableTools.get(index));
                        } else {
                            throw new IndexOutOfBoundsException();
                        }
                    }
                    
                    selectedTools = new ArrayList<>(uniqueTools);
                    validSelection = true;
                } catch (Exception e) {
                    System.out.println("❌ Invalid selection. Please try again.");
                }
            }
        }

        System.out.println("\n✅ Selected tools: " + String.join(", ", selectedTools));
        return selectedTools;
    }

    private static List<String> configureCommands(Scanner scanner, List<String> tools, String domain) {
        System.out.println("\n⚙️  Command Configuration:");
        System.out.println("=".repeat(40));
        
        List<String> commands = new ArrayList<>();
        
        for (String tool : tools) {
            String defaultCmd = String.format(DEFAULT_COMMANDS.get(tool), domain);
            System.out.println("\n🔧 Tool: " + tool);
            System.out.println("📝 Default command: " + defaultCmd);
            System.out.print("Use default? (y/n/custom): ");
            
            String choice = scanner.nextLine().trim().toLowerCase();
            
            switch (choice) {
                case "y":
                case "yes":
                case "":
                    commands.add(defaultCmd);
                    break;
                case "n":
                case "no":
                    System.out.println("⏭️  Skipping " + tool);
                    break;
                case "custom":
                case "c":
                    System.out.print("Enter custom command for " + tool + ": ");
                    String customCmd = scanner.nextLine().trim();
                    if (!customCmd.isEmpty()) {
                        commands.add(customCmd);
                    }
                    break;
                default:
                    commands.add(defaultCmd);
                    break;
            }
        }
        
        return commands;
    }

    private static Set<String> executeCommands(List<String> commands) {
        Set<String> allSubdomains = Collections.synchronizedSet(new HashSet<>());
        
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i);
            System.out.printf("\n[%d/%d] 🚀 Running: %s\n", i + 1, commands.size(), command);
            
            long startTime = System.currentTimeMillis();
            Set<String> results = runCommand(command);
            long endTime = System.currentTimeMillis();
            
            System.out.printf("✅ Found %d subdomains in %.2fs\n", 
                            results.size(), (endTime - startTime) / 1000.0);
            
            allSubdomains.addAll(results);
        }
        
        return allSubdomains;
    }

    private static Set<String> runCommand(String command) {
        Set<String> outputLines = new HashSet<>();
        try {
            ProcessBuilder builder = new ProcessBuilder("bash", "-c", command);
            builder.redirectErrorStream(true); // Combine stderr with stdout
            Process process = builder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && isValidSubdomain(line)) {
                        outputLines.add(line.toLowerCase()); // Normalize to lowercase
                    }
                }
            }

            // Wait for process to complete with timeout
            if (!process.waitFor(300, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                System.out.println("⚠️  Command timed out and was terminated");
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("❌ Error running command: " + e.getMessage());
        }
        return outputLines;
    }

    private static boolean isValidSubdomain(String subdomain) {
        // Basic validation for subdomain format
        return subdomain.matches("^[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}$") &&
               !subdomain.startsWith(".") &&
               !subdomain.endsWith(".") &&
               subdomain.length() > 3;
    }

    private static Set<String> checkLiveSubdomains(Set<String> subdomains, EnumerationConfig config) {
        System.out.println("\n🔍 Checking live subdomains...");
        System.out.println("=".repeat(35));
        
        Set<String> liveSubdomains = Collections.synchronizedSet(new HashSet<>());
        ExecutorService executor = Executors.newFixedThreadPool(config.maxThreads);
        List<Future<Void>> futures = new ArrayList<>();
        
        long startTime = System.currentTimeMillis();
        int totalSubdomains = subdomains.size();
        
        for (String subdomain : subdomains) {
            Future<Void> future = executor.submit(() -> {
                if (isSubdomainLive(subdomain, config.connectionTimeout)) {
                    liveSubdomains.add(subdomain);
                    System.out.printf("✅ Live: %s\n", subdomain);
                }
                return null;
            });
            futures.add(future);
        }
        
        // Wait for all tasks to complete
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                System.err.println("❌ Error during live check: " + e.getMessage());
            }
        }
        
        executor.shutdown();
        long endTime = System.currentTimeMillis();
        
        System.out.printf("\n📊 Live check completed in %.2fs\n", (endTime - startTime) / 1000.0);
        System.out.printf("🎯 Found %d/%d live subdomains\n", liveSubdomains.size(), totalSubdomains);
        
        return liveSubdomains;
    }
    
    private static boolean isSubdomainLive(String subdomain, int timeout) {
        // Try both HTTP and HTTPS
        String[] protocols = {"https://", "http://"};
        
        for (String protocol : protocols) {
            try {
                URL url = new URL(protocol + subdomain);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(timeout);
                connection.setReadTimeout(timeout);
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
    
    private static void captureScreenshots(Set<String> liveSubdomains, EnumerationConfig config) {
        System.out.println("\n📸 Capturing screenshots with GoWitness...");
        System.out.println("=".repeat(45));
        
        // Create screenshots directory
        File screenshotDir = new File(config.screenshotOutputDir);
        if (!screenshotDir.exists()) {
            screenshotDir.mkdirs();
            System.out.println("📁 Created screenshots directory: " + config.screenshotOutputDir);
        }
        
        // Create a file with live subdomains for GoWitness
        String subdomainListFile = config.screenshotOutputDir + "/target_urls.txt";
        try (PrintWriter writer = new PrintWriter(subdomainListFile)) {
            for (String subdomain : liveSubdomains) {
                writer.println("https://" + subdomain);
                writer.println("http://" + subdomain);
            }
            System.out.println("📝 Target URLs file created: " + subdomainListFile);
        } catch (IOException e) {
            System.err.println("❌ Error creating target URLs file: " + e.getMessage());
            return;
        }
        
        // Run GoWitness
        String goWitnessCommand = String.format(
    "gowitness scan file -f %s --no-http --screenshot-path %s ",
            subdomainListFile, config.screenshotOutputDir
        );
        
        System.out.println("🚀 Running GoWitness command:");
        System.out.println("   " + goWitnessCommand);
        System.out.println("\nThis may take a while depending on the number of live subdomains...");
        
        try {
            ProcessBuilder builder = new ProcessBuilder("bash", "-c", goWitnessCommand);
            Process process = builder.start();
            
            // Monitor progress
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                int screenshotCount = 0;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("screenshot") || line.contains("Screenshotting")) {
                        screenshotCount++;
                        System.out.printf("📷 Screenshot %d completed\n", screenshotCount);
                    } else if (!line.trim().isEmpty()) {
                        System.out.println("ℹ️  " + line);
                    }
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("\n✅ Screenshots captured successfully!");
                System.out.println("📁 Screenshots saved to: " + config.screenshotOutputDir);
                
                // Generate HTML report if available
                generateScreenshotReport(config.screenshotOutputDir);
            } else {
                System.out.println("❌ GoWitness failed with exit code: " + exitCode);
                System.out.println("💡 Make sure GoWitness is installed and accessible");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error running GoWitness: " + e.getMessage());
            System.out.println("💡 Installation: go install github.com/sensepost/gowitness@latest");
        }
    }
    
    private static void generateScreenshotReport(String outputDir) {
        try {
            String reportCommand = String.format("gowitness report generate -P %s --sort-perception", outputDir);
            ProcessBuilder builder = new ProcessBuilder("bash", "-c", reportCommand);
            Process process = builder.start();
            
            if (process.waitFor() == 0) {
                System.out.println("📋 HTML report generated: " + outputDir + "/report.html");
            }
        } catch (Exception e) {
            // Silently fail - report generation is optional
        }
    }

    private static void displayFinalSummary(Set<String> allSubdomains, Set<String> liveSubdomains, 
                                           List<String> tools, EnumerationConfig config, String outputFolder) {
        System.out.println("\n" + "=".repeat(65));
        System.out.println("🎊 FINAL ENUMERATION SUMMARY");
        System.out.println("=".repeat(65));
        
        System.out.println("📊 STATISTICS:");
        
        if (config.findSubdomains && !tools.isEmpty()) {
            System.out.println("  🎯 Tools used: " + String.join(", ", tools));
            System.out.println("  🔍 Total subdomains discovered: " + allSubdomains.size());
        }
        
        if (config.checkLiveSubdomains) {
            System.out.println("  ✅ Live subdomains found: " + liveSubdomains.size());
            if (allSubdomains.size() > 0) {
                double livePercentage = (double) liveSubdomains.size() / allSubdomains.size() * 100;
                System.out.printf("  📈 Success rate: %.1f%%\n", livePercentage);
            }
        }
        
        if (config.captureScreenshots) {
            System.out.println("  📸 Screenshots captured: " + liveSubdomains.size() + " targets");
        }
        
        if (!outputFolder.isEmpty()) {
            System.out.println("\n📁 OUTPUT FILES:");
            System.out.println("  📂 Main folder: " + outputFolder);
            
            if (config.findSubdomains && !allSubdomains.isEmpty()) {
                System.out.println("  📄 All subdomains: " + outputFolder + "/[domain]_all_subdomains.txt");
            }
            
            if (config.checkLiveSubdomains && !liveSubdomains.isEmpty()) {
                System.out.println("  📄 Live subdomains: " + outputFolder + "/[domain]_live_subdomains.txt");
                System.out.println("  📄 Live URLs: " + outputFolder + "/[domain]_live_urls.txt");
            }
            
            if (config.captureScreenshots) {
                System.out.println("  📸 Screenshots: " + outputFolder + "/screenshots/");
                System.out.println("  📋 HTML Report: " + outputFolder + "/screenshots/report.html");
            }
        }
        
        // Display sample results
        Set<String> displaySet = new HashSet<>();
        String displayType = "";
        
        if (config.checkLiveSubdomains && !liveSubdomains.isEmpty()) {
            displaySet = liveSubdomains;
            displayType = "LIVE SUBDOMAINS";
        } else if (config.findSubdomains && !allSubdomains.isEmpty()) {
            displaySet = allSubdomains;
            displayType = "DISCOVERED SUBDOMAINS";
        }
        
        if (!displaySet.isEmpty()) {
            System.out.println("\n📋 " + displayType + ":");
            if (displaySet.size() <= 12) {
                displaySet.stream().sorted().forEach(s -> System.out.println("  • " + s));
            } else {
                displaySet.stream().sorted().limit(12)
                         .forEach(s -> System.out.println("  • " + s));
                System.out.println("  ... and " + (displaySet.size() - 12) + " more (see output files)");
            }
        }
        
        System.out.println("\n" + "=".repeat(65));
        System.out.println("🎉 SUBMERGEX OPERATIONS COMPLETED SUCCESSFULLY!");
        System.out.println("=".repeat(65));
        
        if (config.checkLiveSubdomains && !liveSubdomains.isEmpty() && !outputFolder.isEmpty()) {
            System.out.println("\n💡 NEXT STEPS - Use your results with:");
            System.out.println("  🔥 Vulnerability scanning:");
            System.out.println("     nuclei -l " + outputFolder + "/[domain]_live_urls.txt");
            System.out.println("  🗺️  Port scanning:");
            System.out.println("     nmap -iL " + outputFolder + "/[domain]_live_subdomains.txt");
            System.out.println("  📁 Directory enumeration:");
            System.out.println("     gobuster dir -u [url] -w [wordlist]");
            System.out.println("  🕸️  Web technology detection:");
            System.out.println("     httpx -l " + outputFolder + "/[domain]_live_subdomains.txt -tech-detect");
        }
    }
}