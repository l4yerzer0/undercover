package cli

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/spf13/cobra"
	"undercover/internal/engine"
	"undercover/internal/ui"
)

var statusCmd = &cobra.Command{
	Use:   "status",
	Short: "Show service status, sing-box version, uptime and users count",
	RunE: func(cmd *cobra.Command, args []string) error {
		target, host, err := connectTarget()
		if err != nil {
			return err
		}
		defer target.Close()

		st, err := engine.GetStatus(target, host)
		if err != nil {
			ui.Error("Failed to query status", err.Error(), "")
			return err
		}

		if FlagJSON {
			data, _ := json.MarshalIndent(st, "", "  ")
			fmt.Println(string(data))
			return nil
		}

		fmt.Println()
		stateBadge := ui.SuccessStyle.Render("[ACTIVE]")
		if st.ServiceState != "active" {
			stateBadge = ui.ErrorStyle.Render("[" + strings.ToUpper(st.ServiceState) + "]")
		}

		header := fmt.Sprintf("SERVER STATUS // %s (Port: %d)", host, st.Port)
		fmt.Println(ui.TitleStyle.Render(header))
		fmt.Println()

		protoDisplay := "VLESS + Reality (XTLS-Vision)"
		if st.Protocol == "hysteria2" {
			protoDisplay = "Hysteria 2 (QUIC/UDP)"
		}

		card := fmt.Sprintf(
			"SERVICE:    %s\nUPTIME:     %s\nPROTOCOL:   %s\nCORE:       sing-box v%s\nSNI MASK:   %s\nUSERS:      %d identity(ies)",
			stateBadge, st.Uptime, protoDisplay, st.SingBoxVersion, st.ServerName, st.UsersCount,
		)
		fmt.Println(ui.DossierCard.Render(card))
		fmt.Println()

		return nil
	},
}
