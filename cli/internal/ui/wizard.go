package ui

import (
	"fmt"
	"os"
	"strings"

	"github.com/charmbracelet/huh"
	"undercover/internal/singbox"
)

type DeployOptions struct {
	Host       string
	SSHPort    int
	SSHUser    string
	AuthMethod string // "key" or "password"
	KeyPath    string
	Password   string
	Protocol   string // "vless-reality" or "hysteria2"
	VPNPort    int
	ServerName string
	UserName   string
}

// AskDeployParams runs the interactive TUI wizard to collect deployment configuration
func AskDeployParams(defaults DeployOptions) (*DeployOptions, error) {
	opts := defaults
	if opts.Protocol == "" {
		opts.Protocol = singbox.ProtocolVLESSReality
	}
	if opts.SSHPort <= 0 {
		opts.SSHPort = 22
	}
	if opts.SSHUser == "" {
		opts.SSHUser = "root"
	}
	if opts.VPNPort <= 0 {
		opts.VPNPort = 443
	}
	if opts.UserName == "" {
		opts.UserName = "default"
	}
	if opts.AuthMethod == "" {
		opts.AuthMethod = "key"
	}

	sshPortStr := fmt.Sprintf("%d", opts.SSHPort)
	vpnPortStr := fmt.Sprintf("%d", opts.VPNPort)

	// Step 1: Server connection details
	form1 := huh.NewForm(
		huh.NewGroup(
			huh.NewInput().
				Title("Server Address").
				Description("IPv4, IPv6 or hostname of your VPS").
				Placeholder("198.51.100.1").
				Value(&opts.Host).
				Validate(func(s string) error {
					if strings.TrimSpace(s) == "" {
						return fmt.Errorf("server address cannot be empty")
					}
					return nil
				}),

			huh.NewInput().
				Title("SSH Port").
				Description("Port for SSH connection (default 22)").
				Value(&sshPortStr),

			huh.NewInput().
				Title("SSH User").
				Description("Root or sudo user on the remote machine").
				Value(&opts.SSHUser),

			huh.NewSelect[string]().
				Title("SSH Authentication").
				Options(
					huh.NewOption("SSH Key (recommended)", "key"),
					huh.NewOption("Password", "password"),
				).
				Value(&opts.AuthMethod),
		),
	)

	if err := form1.Run(); err != nil {
		return nil, err
	}

	var p int
	if _, err := fmt.Sscanf(sshPortStr, "%d", &p); err == nil && p > 0 {
		opts.SSHPort = p
	}

	// Step 2: Auth credential
	if opts.AuthMethod == "key" {
		home, _ := os.UserHomeDir()
		defaultKey := fmt.Sprintf("%s/.ssh/id_ed25519", home)
		if _, err := os.Stat(defaultKey); os.IsNotExist(err) {
			defaultKey = fmt.Sprintf("%s/.ssh/id_rsa", home)
		}
		opts.KeyPath = defaultKey

		formKey := huh.NewForm(
			huh.NewGroup(
				huh.NewInput().
					Title("Private Key Path").
					Description("Path to your local SSH private key").
					Value(&opts.KeyPath),
			),
		)
		if err := formKey.Run(); err != nil {
			return nil, err
		}
	} else {
		formPass := huh.NewForm(
			huh.NewGroup(
				huh.NewInput().
					Title("SSH Password").
					Description("Password will NOT be stored locally").
					EchoMode(huh.EchoModePassword).
					Value(&opts.Password),
			),
		)
		if err := formPass.Run(); err != nil {
			return nil, err
		}
	}

	// Step 3: VPN protocol parameters
	selectedDomain := singbox.RecommendedDomains[0]
	domainOptions := make([]huh.Option[string], 0, len(singbox.RecommendedDomains)+1)
	for _, d := range singbox.RecommendedDomains {
		domainOptions = append(domainOptions, huh.NewOption(d, d))
	}
	domainOptions = append(domainOptions, huh.NewOption("Custom domain...", "custom"))

	form3 := huh.NewForm(
		huh.NewGroup(
			huh.NewSelect[string]().
				Title("VPN Protocol").
				Description("Choose protocol based on your network conditions").
				Options(
					huh.NewOption("VLESS + Reality (TCP, camouflage to popular domains, default)", singbox.ProtocolVLESSReality),
					huh.NewOption("Hysteria 2 (UDP/QUIC, maximum speed & packet loss resistance)", singbox.ProtocolHysteria2),
				).
				Value(&opts.Protocol),

			huh.NewInput().
				Title("VPN Service Port").
				Description("Standard port for TLS (443 is recommended)").
				Value(&vpnPortStr),

			huh.NewSelect[string]().
				Title("Camouflage TLS SNI").
				Description("TLS 1.3 domain used to disguise VPN handshake").
				Options(domainOptions...).
				Value(&selectedDomain),

			huh.NewInput().
				Title("First Client Name").
				Description("Identifier for the initial generated share link").
				Value(&opts.UserName),
		),
	)

	if err := form3.Run(); err != nil {
		return nil, err
	}

	if _, err := fmt.Sscanf(vpnPortStr, "%d", &p); err == nil && p > 0 {
		opts.VPNPort = p
	}

	if selectedDomain == "custom" {
		customInput := ""
		formCustom := huh.NewForm(
			huh.NewGroup(
				huh.NewInput().
					Title("Custom Domain").
					Description("Must support TLS 1.3 and HTTP/2 (e.g. cloudflare.com)").
					Value(&customInput),
			),
		)
		if err := formCustom.Run(); err != nil {
			return nil, err
		}
		opts.ServerName = strings.TrimSpace(customInput)
	} else {
		opts.ServerName = selectedDomain
	}

	return &opts, nil
}

// ConfirmAction prompts the user with a confirmation dialog
func ConfirmAction(title, description string, defaultVal bool) (bool, error) {
	confirmed := defaultVal
	form := huh.NewForm(
		huh.NewGroup(
			huh.NewConfirm().
				Title(title).
				Description(description).
				Value(&confirmed),
		),
	)
	err := form.Run()
	return confirmed, err
}

// PromptHostKey handles interactive TOFU prompt for new SSH host fingerprints
func PromptHostKey(host, keyType, fingerprint string) (bool, error) {
	fmt.Printf("\nNew SSH host key encountered for %s\n", host)
	fmt.Printf("Key type:    %s\n", keyType)
	fmt.Printf("Fingerprint: %s\n\n", fingerprint)

	return ConfirmAction(
		"Trust this host key?",
		"The authenticity of host can't be established. Are you sure you want to continue connecting?",
		true,
	)
}
