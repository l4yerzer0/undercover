package cli

import (
	"github.com/spf13/cobra"
	"undercover/internal/engine"
	"undercover/internal/singbox"
	"undercover/internal/ui"
)

var flagUpdateVersion string

var updateCmd = &cobra.Command{
	Use:   "update",
	Short: "Update sing-box core binary with automatic rollback on validation error",
	RunE: func(cmd *cobra.Command, args []string) error {
		targetVersion := flagUpdateVersion
		if targetVersion == "" {
			targetVersion = singbox.DefaultSingBoxVersion
		}

		target, _, err := connectTarget()
		if err != nil {
			return err
		}
		defer target.Close()

		if err := engine.UpdateServer(target, targetVersion); err != nil {
			ui.Error("Update failed", err.Error(), "")
			return err
		}

		ui.Success("Core update completed successfully!")
		return nil
	},
}

func init() {
	updateCmd.Flags().StringVar(&flagUpdateVersion, "version", "", "Specific sing-box version to install (defaults to pinned version)")
}
