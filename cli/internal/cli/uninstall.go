package cli

import (
	"fmt"

	"github.com/spf13/cobra"
	"undercover/internal/engine"
	"undercover/internal/ui"
)

var uninstallCmd = &cobra.Command{
	Use:   "uninstall",
	Short: "Completely remove Undercover VPN service, configs, binary, and firewall rule",
	RunE: func(cmd *cobra.Command, args []string) error {
		if !FlagYes {
			confirmed, err := ui.ConfirmAction(
				"Confirm Uninstall",
				"This will stop the VPN service, delete all configuration, and remove sing-box from the server. Proceed?",
				false,
			)
			if err != nil || !confirmed {
				fmt.Println("Aborted.")
				return nil
			}
		}

		target, host, err := connectTarget()
		if err != nil {
			return err
		}
		defer target.Close()

		ui.StepStatus(false, "Uninstall", fmt.Sprintf("Removing Undercover from %s...", host))
		if err := engine.UninstallServer(target); err != nil {
			ui.Error("Uninstall failed", err.Error(), "")
			return err
		}

		ui.StepStatus(true, "Uninstall", "Service, configs, binary, and firewall rule removed")
		ui.Success("Undercover VPN successfully uninstalled from %s", host)
		return nil
	},
}
