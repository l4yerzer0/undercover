package cli

import (
	"os"

	"github.com/spf13/cobra"
	"undercover/internal/ui"
)

var (
	Version = "1.0.0"

	// Global flags
	FlagHost      string
	FlagSSHPort   int
	FlagSSHUser   string
	FlagSSHKey    string
	FlagPassword  string
	FlagLocal     bool
	FlagYes       bool
	FlagJSON      bool
	FlagNoColor   bool
)

var RootCmd = &cobra.Command{
	Use:   "uc",
	Short: "Undercover CLI // Personal VPN server deployment & management",
	Long: `Undercover CLI is a fast, dependency-free utility to deploy and manage
your personal private VPN server (sing-box VLESS + Reality) on your own VPS.`,
	PersistentPreRun: func(cmd *cobra.Command, args []string) {
		if FlagNoColor {
			_ = os.Setenv("NO_COLOR", "1")
		}
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		// If invoked directly without subcommands, open interactive main menu
		return RunInteractiveMenu()
	},
}

func init() {
	RootCmd.PersistentFlags().StringVar(&FlagHost, "host", "", "Target server IP address or hostname")
	RootCmd.PersistentFlags().IntVar(&FlagSSHPort, "ssh-port", 22, "SSH port")
	RootCmd.PersistentFlags().StringVar(&FlagSSHUser, "ssh-user", "root", "SSH username")
	RootCmd.PersistentFlags().StringVar(&FlagSSHKey, "ssh-key", "", "Path to SSH private key")
	RootCmd.PersistentFlags().StringVar(&FlagPassword, "password", "", "SSH password (leave empty to use key)")
	RootCmd.PersistentFlags().BoolVar(&FlagLocal, "local", false, "Execute directly on local machine (when uc is installed on the VPS)")
	RootCmd.PersistentFlags().BoolVarP(&FlagYes, "yes", "y", false, "Automatic yes to prompts; run non-interactively")
	RootCmd.PersistentFlags().BoolVar(&FlagJSON, "json", false, "Output results in JSON format")
	RootCmd.PersistentFlags().BoolVar(&FlagNoColor, "no-color", false, "Disable colored terminal output")

	// Add subcommands
	RootCmd.AddCommand(deployCmd)
	RootCmd.AddCommand(usersCmd)
	RootCmd.AddCommand(statusCmd)
	RootCmd.AddCommand(doctorCmd)
	RootCmd.AddCommand(logsCmd)
	RootCmd.AddCommand(exportCmd)
	RootCmd.AddCommand(uninstallCmd)
	RootCmd.AddCommand(updateCmd)
}

func Execute() {
	if err := RootCmd.Execute(); err != nil {
		ui.Error("Command Execution Failed", err.Error(), "Check 'uc --help' for command syntax")
		os.Exit(1)
	}
}
