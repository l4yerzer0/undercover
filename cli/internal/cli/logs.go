package cli

import (
	"fmt"

	"github.com/spf13/cobra"
	"undercover/internal/engine"
	"undercover/internal/ui"
)

var flagLogLines int

var logsCmd = &cobra.Command{
	Use:   "logs",
	Short: "Stream recent systemd service logs",
	RunE: func(cmd *cobra.Command, args []string) error {
		target, _, err := connectTarget()
		if err != nil {
			return err
		}
		defer target.Close()

		logs, err := engine.GetLogs(target, flagLogLines)
		if err != nil {
			ui.Error("Failed to fetch logs", err.Error(), "")
			return err
		}

		fmt.Println(logs)
		return nil
	},
}

func init() {
	logsCmd.Flags().IntVarP(&flagLogLines, "lines", "n", 50, "Number of log lines to show")
}
