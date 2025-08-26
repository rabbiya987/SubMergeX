# SubMergeX

SubMergeX is a **Java-based subdomain enumeration and reconnaissance framework** designed for security researchers, penetration testers, and bug bounty hunters. It automates the process of discovering subdomains, checking which ones are live, capturing screenshots, and generating organized reports.

---

## Features

- **Multi-tool Subdomain Discovery**: Integrates popular tools like `subfinder`, `assetfinder`, `amass`, `findomain`, `crt.sh`, and `Sublist3r`.
- **Live Subdomain Checking**: Supports `httprobe`, `httpx`, or a custom Java-based HTTP checker.
- **Automated Screenshots**: Uses `GoWitness` to capture screenshots of live subdomains and generates HTML reports.
- **Organized Output**: Creates timestamped folders, saves discovered and live subdomains, live URLs, and generates a summary report.
- **Customizable Commands**: Option to use default, skip, or custom commands for each tool.
- **Cross-platform**: Runs on Linux and Windows (requires installed tools).

---

## Installation

1. Clone the repository:

```bash
git clone https://github.com/yourusername/SubMergeX.git
cd SubMergeX
````

2. Ensure Java (JDK 11+) is installed:

```bash
java -version
```

3. Install required subdomain and live checking tools:

* **Subdomain tools**: subfinder, assetfinder, amass, findomain, Sublist3r
* **Live checking tools**: httprobe, httpx
* **Screenshot tool**: GoWitness

> Some tools require `Go` or `Python` depending on the tool.

---

## Usage

Compile the project:

```bash
javac *.java
```

Run the main program:

```bash
java SubMergeXMain
```

### Workflow

1. Enter the **target domain**.
2. Select the **subdomain enumeration tools**.
3. Configure commands or use defaults.
4. Check which subdomains are **live**.
5. Capture **screenshots** of live subdomains (optional).
6. Outputs and summary reports will be saved in a **timestamped folder**.

---

## Output

The tool generates:

* `all_subdomains.txt` – All discovered subdomains.
* `live_subdomains.txt` – Live subdomains.
* `live_urls.txt` – HTTP/HTTPS variants of live subdomains.
* `screenshots/` – Directory containing screenshots and HTML report (if enabled).
* `summary_report.txt` – Complete enumeration summary.

---

## Example

```text
Domain: example.com
Tools used: subfinder, assetfinder, amass
Total subdomains found: 120
Live subdomains: 45
Screenshots captured: Yes
```

---

## Contributing

Contributions are welcome! Please open issues or submit pull requests to improve functionality, add new tools, or optimize performance.

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

---


