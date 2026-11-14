package cli

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/spf13/cobra"
	"undercover/internal/client"
	"undercover/internal/share"
	"undercover/internal/state"
	"undercover/internal/ui"
)

var connectCmd = &cobra.Command{
	Use:   "connect [server-alias | share-url]",
	Short: "Connect your local machine to VPN via sing-box SOCKS5/HTTP proxy",
	RunE: func(cmd *cobra.Command, args []string) error {
		store, err := state.LoadLocalStore()
		if err != nil {
			return err
		}

		var targetServer *state.LocalServer
		var userAuth string

		if len(args) > 0 {
			arg := args[0]
			if strings.Contains(arg, "://") {
				// Parse vless:// or hysteria2:// URL directly
				parsed, err := share.ParseShareLink(arg)
				if err != nil {
					return fmt.Errorf("invalid share url: %w", err)
				}
				userAuth = parsed.Auth
				alias := parsed.Alias
				if alias == "" {
					alias = fmt.Sprintf("Undercover-%s", parsed.Host)
				}
				targetServer = &state.LocalServer{
					Host:          parsed.Host,
					VPNPort:       parsed.Port,
					Protocol:      parsed.Protocol,
					ServerName:    parsed.ServerName,
					PublicKey:     parsed.PublicKey,
					ShortID:       parsed.ShortID,
					CertPinSha256: parsed.PinSHA256,
					UserAuth:      parsed.Auth,
					ShareLink:     arg,
					Alias:         alias,
				}
				store.AddOrUpdateServer(*targetServer)
				_ = state.SaveLocalStore(store)
			} else {
				// Lookup by alias or host
				for _, s := range store.Servers {
					if s.Alias == arg || s.Host == arg {
						targetServer = &s
						if userAuth == "" && s.UserAuth != "" {
							userAuth = s.UserAuth
						}
						break
					}
				}
			}
		}

		// Fallback to last deployed server if none specified
		if targetServer == nil {
			if len(store.Servers) == 0 {
				return fmt.Errorf("no VPN servers configured. Import a link first with 'uc import <url>' or provide a vless:// or hysteria2:// URL")
			}
			targetServer = &store.Servers[len(store.Servers)-1]
		}

		if userAuth == "" && targetServer.UserAuth != "" {
			userAuth = targetServer.UserAuth
		}

		if userAuth == "" {
			// Connect to remote target to fetch user credentials if needed
			execTarget, _, err := connectTarget()
			if err == nil {
				defer execTarget.Close()
				data, _ := execTarget.ReadFile("/etc/undercover/state.json")
				var st state.RemoteState
				if json.Unmarshal(data, &st) == nil && len(st.Users) > 0 {
					if st.Protocol == "hysteria2" {
						userAuth = st.Users[0].Password
					} else {
						userAuth = st.Users[0].UUID
					}
					if targetServer.Protocol == "" {
						targetServer.Protocol = st.Protocol
					}
				}
			}
		}

		if userAuth == "" {
			userAuth = "11111111-2222-3333-4444-555555555555" // fallback
		}

		if err := client.Connect(*targetServer, userAuth, client.DefaultLocalProxyPort); err != nil {
			ui.Error("Connection Failed", err.Error(), "")
			return err
		}

		protoName := "VLESS+Reality"
		if targetServer.Protocol == "hysteria2" {
			protoName = "Hysteria 2 (QUIC)"
		}

		ui.StepStatus(true, "VPN Connect", fmt.Sprintf("Tunnel established to %s (%s)", targetServer.Host, protoName))
		fmt.Println()
		ui.Success("Connected to Undercover VPN!")
		fmt.Println()

		card := fmt.Sprintf(
			"STATUS:      [OK: ACTIVE]\nSERVER:      %s:%d\nPROTOCOL:    %s\nSNI MASK:    %s\nLOCAL PROXY: 127.0.0.1:%d (SOCKS5/HTTP)\nENV:         export all_proxy=socks5://127.0.0.1:%d",
			targetServer.Host, targetServer.VPNPort, protoName, targetServer.ServerName, client.DefaultLocalProxyPort, client.DefaultLocalProxyPort,
		)
		fmt.Println(ui.DossierCard.Render(card))
		fmt.Println()
		fmt.Println(ui.MutedStyle.Render("To disconnect: 'uc disconnect'"))

		return nil
	},
}

var disconnectCmd = &cobra.Command{
	Use:   "disconnect",
	Short: "Disconnect active local VPN client and restore system network settings",
	RunE: func(cmd *cobra.Command, args []string) error {
		if err := client.Disconnect(); err != nil {
			ui.Warning(err.Error())
			return nil
		}
		ui.Success("VPN disconnected. System network proxies restored.")
		return nil
	},
}

var importCmd = &cobra.Command{
	Use:   "import <share-url>",
	Short: "Import a vless:// or hysteria2:// share link into local client profiles",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		rawLink := args[0]
		parsed, err := share.ParseShareLink(rawLink)
		if err != nil {
			ui.Error("Import Failed", err.Error(), "Ensure URL begins with vless:// or hysteria2://")
			return err
		}

		store, err := state.LoadLocalStore()
		if err != nil {
			return err
		}

		alias := parsed.Alias
		if alias == "" {
			alias = fmt.Sprintf("Undercover-%s", parsed.Host)
		}

		srv := state.LocalServer{
			Host:          parsed.Host,
			VPNPort:       parsed.Port,
			Protocol:      parsed.Protocol,
			ServerName:    parsed.ServerName,
			PublicKey:     parsed.PublicKey,
			ShortID:       parsed.ShortID,
			CertPinSha256: parsed.PinSHA256,
			UserAuth:      parsed.Auth,
			ShareLink:     rawLink,
			Alias:         alias,
		}

		store.AddOrUpdateServer(srv)
		if err := state.SaveLocalStore(store); err != nil {
			return err
		}

		ui.Success("Profile '%s' (%s:%d, %s) successfully imported!", srv.Alias, srv.Host, srv.VPNPort, srv.Protocol)
		return nil
	},
}

func init() {
	RootCmd.AddCommand(connectCmd)
	RootCmd.AddCommand(disconnectCmd)
	RootCmd.AddCommand(importCmd)
}
