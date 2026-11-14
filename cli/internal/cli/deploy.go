package cli

import (
	"encoding/json"
	"fmt"
	"os"
	"runtime"

	"github.com/spf13/cobra"
	"undercover/internal/engine"
	"undercover/internal/executor"
	"undercover/internal/singbox"
	"undercover/internal/ui"
)

var (
	flagVPNPort    int
	flagSNI        string
	flagClientName string
	flagProtocol   string
)

var deployCmd = &cobra.Command{
	Use:   "deploy",
	Short: "Deploy Undercover VPN server on target VPS",
	Long:  "Connects to the specified remote host (or local server), installs sing-box, configures VLESS+Reality or Hysteria2, and displays connection link & QR.",
	RunE: func(cmd *cobra.Command, args []string) error {
		if !FlagJSON {
			ui.PrintBanner(os.Stdout, Version)
		}

		opts := ui.DeployOptions{
			Host:       FlagHost,
			SSHPort:    FlagSSHPort,
			SSHUser:    FlagSSHUser,
			KeyPath:    FlagSSHKey,
			Password:   FlagPassword,
			Protocol:   flagProtocol,
			VPNPort:    flagVPNPort,
			ServerName: flagSNI,
			UserName:   flagClientName,
		}

		isLinuxRoot := runtime.GOOS == "linux" && os.Geteuid() == 0

		// Interactive mode if host not provided and --local and --yes are not set
		if opts.Host == "" && !FlagLocal && !isLinuxRoot && !FlagYes {
			collected, err := ui.AskDeployParams(opts)
			if err != nil {
				return err
			}
			opts = *collected
		} else {
			if opts.Host == "" && (FlagLocal || isLinuxRoot) {
				opts.Host = executor.DetectPublicIP()
			}
			if opts.VPNPort <= 0 {
				opts.VPNPort = 443
			}
			if opts.ServerName == "" {
				opts.ServerName = singbox.RecommendedDomains[0]
			}
			if opts.UserName == "" {
				opts.UserName = "default"
			}
		}

		promptHost := ui.PromptHostKey
		if FlagYes {
			promptHost = func(h, kt, fp string) (bool, error) {
				return true, nil
			}
		}

		var target executor.TargetExecutor
		if FlagLocal || (isLinuxRoot && FlagHost == "") {
			target = executor.NewLocalExecutor(opts.Host)
		}

		res, err := engine.DeployServer(target, opts, promptHost)
		if err != nil {
			ui.Error("Deployment Failed", err.Error(), "Verify server credentials, root access, and network reachability")
			return err
		}

		if FlagJSON {
			data, _ := json.MarshalIndent(res, "", "  ")
			fmt.Println(string(data))
			return nil
		}

		// Print Dossier Result Card
		fmt.Println()
		ui.Success("Server successfully deployed!")
		fmt.Println()

		protoName := "VLESS + Reality (XTLS-Vision)"
		authInfo := fmt.Sprintf("UUID:       %s", res.UUID)
		if res.Protocol == singbox.ProtocolHysteria2 {
			protoName = "Hysteria 2 (QUIC/UDP)"
			authInfo = fmt.Sprintf("PASSWORD:   %s", res.Password)
		}

		cardContent := fmt.Sprintf(
			"HOST:       %s\nPORT:       %d\nPROTOCOL:   %s\nSNI MASK:   %s\nCORE:       sing-box v%s\nUSER:       %s\n%s",
			res.Host, res.Port, protoName, res.ServerName, res.SingBoxVersion, res.UserName, authInfo,
		)
		fmt.Println(ui.DossierCard.Render(cardContent))
		fmt.Println()

		if res.QRCode != "" {
			fmt.Println("Scan to connect:")
			fmt.Println(res.QRCode)
		}

		fmt.Println("Share Link:")
		fmt.Println(ui.SuccessStyle.Render(res.ShareLink))
		fmt.Println()
		fmt.Println(ui.MutedStyle.Render("Manage users: 'uc users add <name>' | Check state: 'uc status'"))

		return nil
	},
}

func init() {
	deployCmd.Flags().StringVar(&flagProtocol, "protocol", "vless", "VPN protocol: vless (default) or hysteria2")
	deployCmd.Flags().IntVar(&flagVPNPort, "vpn-port", 443, "VPN service port")
	deployCmd.Flags().StringVar(&flagSNI, "sni", "gateway.icloud.com", "Camouflage TLS 1.3 SNI domain")
	deployCmd.Flags().StringVar(&flagClientName, "user", "default", "Initial client identity name")
}
