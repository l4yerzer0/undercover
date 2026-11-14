package engine

import (
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"undercover/internal/doctor"
	"undercover/internal/executor"
	"undercover/internal/firewall"
	"undercover/internal/server"
	"undercover/internal/share"
	"undercover/internal/singbox"
	"undercover/internal/ssh"
	"undercover/internal/state"
	"undercover/internal/ui"
)

type DeployResult struct {
	ShareLink      string
	QRCode         string
	Host           string
	Port           int
	Protocol       string
	ServerName     string
	SingBoxVersion string
	UserName       string
	UUID           string
	Password       string
	PublicKey      string
	ShortID        string
	CertPinSha256  string
}

// DeployServer executes the full deployment pipeline over TargetExecutor (SSH or Local)
func DeployServer(target executor.TargetExecutor, opts ui.DeployOptions, promptHostKey ssh.HostKeyPromptFunc) (*DeployResult, error) {
	var err error

	// Normalize protocol
	switch strings.ToLower(opts.Protocol) {
	case "hysteria2", "hy2":
		opts.Protocol = singbox.ProtocolHysteria2
	default:
		opts.Protocol = singbox.ProtocolVLESSReality
	}

	// If no executor passed, connect via SSH
	if target == nil {
		ui.StepStatus(false, "SSH Connection", fmt.Sprintf("Connecting to %s:%d as %s...", opts.Host, opts.SSHPort, opts.SSHUser))
		sshCfg := ssh.ClientConfig{
			Host:          opts.Host,
			Port:          opts.SSHPort,
			User:          opts.SSHUser,
			Password:      opts.Password,
			KeyPath:       opts.KeyPath,
			PromptHostKey: promptHostKey,
		}

		client, dialErr := ssh.Dial(sshCfg)
		if dialErr != nil {
			return nil, fmt.Errorf("failed to connect via SSH: %w", dialErr)
		}
		defer client.Close()
		target = executor.NewSSHExecutor(client, opts.Host)
		ui.StepStatus(true, "SSH Connection", fmt.Sprintf("Connected (Fingerprint: %s)", client.HostKeyFingerprint()))
	} else if target.IsLocal() {
		ui.StepStatus(true, "Execution Target", fmt.Sprintf("Running locally on host (IP: %s)", target.Host()))
		if opts.Host == "" {
			opts.Host = target.Host()
		}
	}

	// Step 2: Inspect remote/local server environment
	ui.StepStatus(false, "Server Inspection", "Detecting OS, architecture, and system state...")
	info, err := server.Inspect(target)
	if err != nil {
		return nil, fmt.Errorf("server inspection failed: %w", err)
	}
	ui.StepStatus(true, "Server Inspection", fmt.Sprintf("%s (%s), arch: %s", info.OSName, info.OSVersion, info.Architecture))

	// Check target port
	if !info.IsPortAvailable(opts.VPNPort) {
		ui.Warning("Port %d is already occupied on the server!", opts.VPNPort)
		return nil, fmt.Errorf("port %d is currently in use by another service on the server", opts.VPNPort)
	}

	// Step 3: Ensure time synchronization
	if !info.TimeSynchronized {
		ui.StepStatus(false, "Time Sync", "Synchronizing system clock with NTP...")
		_ = server.EnsureTimeSync(target)
		ui.StepStatus(true, "Time Sync", "NTP synchronization requested")
	} else {
		ui.StepStatus(true, "Time Sync", "Clock is NTP synchronized")
	}

	// Step 4: Download and install sing-box core
	version := singbox.DefaultSingBoxVersion
	ui.StepStatus(false, "Core Installation", fmt.Sprintf("Installing sing-box v%s (%s)...", version, info.Architecture))

	downloadURL, err := singbox.DownloadURL(version, info.Architecture)
	if err != nil {
		return nil, err
	}
	expectedHash, err := singbox.ExpectedChecksum(version, info.Architecture)
	if err != nil {
		return nil, err
	}

	installCmd := fmt.Sprintf(`
set -e
command -v curl >/dev/null 2>&1 || (apt-get update -qq && apt-get install -y -qq curl ca-certificates tar)
curl -sSL -o /tmp/singbox.tar.gz "%s"
echo "%s  /tmp/singbox.tar.gz" | sha256sum -c -
mkdir -p /tmp/singbox_extract
tar -xzf /tmp/singbox.tar.gz -C /tmp/singbox_extract
find /tmp/singbox_extract -name sing-box -type f -exec install -m 0755 {} /usr/local/bin/sing-box \;
rm -rf /tmp/singbox.tar.gz /tmp/singbox_extract
`, downloadURL, expectedHash)

	if out, err := target.RunCombined(installCmd); err != nil {
		return nil, fmt.Errorf("failed to download and verify sing-box: %s (%w)", out, err)
	}
	ui.StepStatus(true, "Core Installation", fmt.Sprintf("sing-box v%s installed and verified", version))

	// Step 5: Create dedicated system user
	userCmd := `
id -u undercover >/dev/null 2>&1 || useradd -r -s /usr/sbin/nologin -d /var/empty -M undercover
mkdir -p /etc/undercover
chown -R undercover:undercover /etc/undercover
chmod 0700 /etc/undercover
`
	if out, err := target.RunCombined(userCmd); err != nil {
		return nil, fmt.Errorf("failed to configure system user: %s (%w)", out, err)
	}

	// Step 6: Generate cryptographic secrets
	var keyPair *singbox.KeyPair
	var userUUID string
	var shortID string
	var userPassword string
	var certPinSha256 string

	if opts.Protocol == singbox.ProtocolHysteria2 {
		ui.StepStatus(false, "Key Generation", "Generating ECDSA P-256 certificate and Hysteria2 user password...")
		userPassword, err = singbox.GeneratePassword()
		if err != nil {
			return nil, err
		}
		certGen, err := singbox.GenerateSelfSignedCert(opts.ServerName)
		if err != nil {
			return nil, fmt.Errorf("failed to generate TLS certificate: %w", err)
		}
		certPinSha256 = certGen.SHA256PinHex

		if err := target.WriteFile("/etc/undercover/cert.pem", certGen.CertPEM, 0644); err != nil {
			return nil, fmt.Errorf("failed to write cert.pem: %w", err)
		}
		if err := target.WriteFile("/etc/undercover/key.pem", certGen.KeyPEM, 0600); err != nil {
			return nil, fmt.Errorf("failed to write key.pem: %w", err)
		}
		_, _ = target.RunCombined("chown undercover:undercover /etc/undercover/cert.pem /etc/undercover/key.pem")
		ui.StepStatus(true, "Key Generation", "ECDSA TLS certificate and password generated")
	} else {
		ui.StepStatus(false, "Key Generation", "Generating X25519 Reality keypair and client UUID...")
		keyPair, err = singbox.GenerateRealityKeyPair()
		if err != nil {
			return nil, err
		}
		userUUID, err = singbox.GenerateUUID()
		if err != nil {
			return nil, err
		}
		shortID, err = singbox.GenerateShortID()
		if err != nil {
			return nil, err
		}
		ui.StepStatus(true, "Key Generation", "Cryptographic credentials generated")
	}

	// Step 7: Build and write server config
	ui.StepStatus(false, "Configuration", "Generating and validating sing-box server config...")
	var cfgBytes []byte
	if opts.Protocol == singbox.ProtocolHysteria2 {
		cfgBytes, err = singbox.BuildServerConfig(singbox.ConfigParams{
			Protocol:        singbox.ProtocolHysteria2,
			Port:            opts.VPNPort,
			ServerName:      opts.ServerName,
			CertificatePath: "/etc/undercover/cert.pem",
			KeyPath:         "/etc/undercover/key.pem",
			Users: []singbox.InboundUser{
				{
					Name:     opts.UserName,
					Password: userPassword,
				},
			},
		})
	} else {
		cfgBytes, err = singbox.BuildServerConfig(singbox.ConfigParams{
			Protocol:   singbox.ProtocolVLESSReality,
			Port:       opts.VPNPort,
			ServerName: opts.ServerName,
			PrivateKey: keyPair.PrivateKey,
			ShortID:    shortID,
			Users: []singbox.InboundUser{
				{
					Name: opts.UserName,
					UUID: userUUID,
					Flow: "xtls-rprx-vision",
				},
			},
		})
	}
	if err != nil {
		return nil, err
	}

	if err := target.WriteFile("/etc/undercover/config.json", cfgBytes, 0600); err != nil {
		return nil, fmt.Errorf("failed to write config file: %w", err)
	}

	if out, err := target.RunCombined("/usr/local/bin/sing-box check -c /etc/undercover/config.json"); err != nil {
		return nil, fmt.Errorf("config syntax validation failed: %s (%w)", out, err)
	}
	ui.StepStatus(true, "Configuration", "Configuration validated with sing-box check")

	// Step 8: Install hardened systemd unit
	unitContent := singbox.SystemdUnitContent()
	if err := target.WriteFile("/etc/systemd/system/undercover.service", []byte(unitContent), 0644); err != nil {
		return nil, fmt.Errorf("failed to write systemd unit: %w", err)
	}
	_, _ = target.RunCombined("systemctl daemon-reload")

	// Step 9: Configure firewall
	fwProto := "tcp"
	if opts.Protocol == singbox.ProtocolHysteria2 {
		fwProto = "udp"
	}
	ui.StepStatus(false, "Firewall", fmt.Sprintf("Applying rules for port %d/%s...", opts.VPNPort, fwProto))
	if err := firewall.AllowPort(target, opts.VPNPort, fwProto); err != nil {
		ui.Warning("Firewall rule update: %v", err)
	} else {
		ui.StepStatus(true, "Firewall", fmt.Sprintf("Port %d/%s configured", opts.VPNPort, fwProto))
	}

	// Step 10: Start and enable service
	ui.StepStatus(false, "Service Start", "Starting undercover systemd service...")
	startCmd := "systemctl enable --now undercover.service"
	if out, err := target.RunCombined(startCmd); err != nil {
		return nil, fmt.Errorf("failed to start service: %s (%w)", out, err)
	}

	var serviceActive bool
	for i := 0; i < 10; i++ {
		time.Sleep(500 * time.Millisecond)
		out, _ := target.RunCombined("systemctl is-active undercover")
		if strings.TrimSpace(out) == "active" {
			serviceActive = true
			break
		}
	}
	if !serviceActive {
		logs, _ := target.RunCombined("journalctl -u undercover -n 20 --no-pager")
		return nil, fmt.Errorf("service failed to start. Logs:\n%s", logs)
	}
	ui.StepStatus(true, "Service Start", "Service is active and running")

	// Step 11: Write server state file
	pubKey := ""
	if keyPair != nil {
		pubKey = keyPair.PublicKey
	}
	remoteState := state.RemoteState{
		Version:        "1.0.0",
		SingBoxVersion: version,
		Protocol:       opts.Protocol,
		Port:           opts.VPNPort,
		ServerName:     opts.ServerName,
		PublicKey:      pubKey,
		ShortID:        shortID,
		CertPinSha256:  certPinSha256,
		CreatedAt:      time.Now(),
		Users: []state.StateUser{
			{
				Name:      opts.UserName,
				UUID:      userUUID,
				Password:  userPassword,
				CreatedAt: time.Now(),
			},
		},
	}
	stateBytes, _ := json.MarshalIndent(remoteState, "", "  ")
	_ = target.WriteFile("/etc/undercover/state.json", stateBytes, 0600)

	// Step 12: Build share link & QR code
	var shareLink string
	authSecret := userUUID
	if opts.Protocol == singbox.ProtocolHysteria2 {
		authSecret = userPassword
		shareLink = share.BuildHysteria2Link(share.Hysteria2Config{
			Password:   userPassword,
			Host:       opts.Host,
			Port:       opts.VPNPort,
			ServerName: opts.ServerName,
			Insecure:   true,
			PinSHA256:  certPinSha256,
			Alias:      opts.UserName,
		})
	} else {
		shareLink = share.BuildVLESSRealityLink(share.VLESSRealityConfig{
			UUID:       userUUID,
			Host:       opts.Host,
			Port:       opts.VPNPort,
			PublicKey:  pubKey,
			ServerName: opts.ServerName,
			ShortID:    shortID,
			Alias:      opts.UserName,
		})
	}

	qrCodeStr, _ := share.GenerateTerminalQRCode(shareLink)

	// Step 13: Save local server bookmark
	localStore, err := state.LoadLocalStore()
	if err == nil {
		localStore.AddOrUpdateServer(state.LocalServer{
			Host:          opts.Host,
			SSHPort:       opts.SSHPort,
			User:          opts.SSHUser,
			KeyPath:       opts.KeyPath,
			Alias:         fmt.Sprintf("Undercover-%s", opts.Host),
			Protocol:      opts.Protocol,
			VPNPort:       opts.VPNPort,
			ServerName:    opts.ServerName,
			PublicKey:     pubKey,
			ShortID:       shortID,
			CertPinSha256: certPinSha256,
			UserAuth:      authSecret,
			ShareLink:     shareLink,
			HostKeySha256: target.HostKeyFingerprint(),
		})
		_ = state.SaveLocalStore(localStore)
	}

	// Step 14: Run diagnostics
	ui.StepStatus(false, "Diagnostics", "Running post-deploy diagnostics...")
	results := doctor.RunDiagnostics(target, opts.Host, opts.VPNPort, &remoteState)
	for _, res := range results {
		if res.Passed {
			ui.StepStatus(true, "Doctor: "+res.Name, res.Message)
		} else {
			ui.Warning("Doctor: %s: %s", res.Name, res.Message)
		}
	}

	return &DeployResult{
		ShareLink:      shareLink,
		QRCode:         qrCodeStr,
		Host:           opts.Host,
		Port:           opts.VPNPort,
		Protocol:       opts.Protocol,
		ServerName:     opts.ServerName,
		SingBoxVersion: version,
		UserName:       opts.UserName,
		UUID:           userUUID,
		Password:       userPassword,
		PublicKey:      pubKey,
		ShortID:        shortID,
		CertPinSha256:  certPinSha256,
	}, nil
}
