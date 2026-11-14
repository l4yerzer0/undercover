package cli

import (
	"encoding/json"
	"fmt"

	"github.com/spf13/cobra"
	"undercover/internal/doctor"
	"undercover/internal/engine"
	"undercover/internal/ui"
)

var doctorCmd = &cobra.Command{
	Use:   "doctor",
	Short: "Run diagnostics on server health, ports, and configuration",
	RunE: func(cmd *cobra.Command, args []string) error {
		target, host, err := connectTarget()
		if err != nil {
			return err
		}
		defer target.Close()

		st, err := engine.LoadServerState(target)
		if err != nil {
			return err
		}

		results := doctor.RunDiagnostics(target, host, st.Port, st)

		if FlagJSON {
			data, _ := json.MarshalIndent(results, "", "  ")
			fmt.Println(string(data))
			return nil
		}

		fmt.Printf("\nUNDERCOVER DOCTOR // %s\n\n", host)
		allPassed := true
		for _, res := range results {
			if res.Passed {
				ui.Success("%-24s %s", res.Name+":", res.Message)
			} else {
				allPassed = false
				ui.Error("Check Failed: "+res.Name, res.Message, "Review logs with 'uc logs'")
			}
		}

		fmt.Println()
		if allPassed {
			ui.Success("All system and security checks passed.")
		} else {
			ui.Warning("One or more diagnostic checks reported issues.")
		}

		return nil
	},
}
