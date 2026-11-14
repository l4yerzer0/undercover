package cli

import (
	"encoding/json"
	"fmt"

	"github.com/spf13/cobra"
	"undercover/internal/engine"
	"undercover/internal/ui"
)

var usersCmd = &cobra.Command{
	Use:   "users",
	Short: "Manage client identities (add, list, remove, show)",
}

var usersAddCmd = &cobra.Command{
	Use:   "add <name>",
	Short: "Create a new client identity and generate share link + QR",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		userName := args[0]
		client, host, err := connectTarget()
		if err != nil {
			return err
		}
		defer client.Close()

		res, err := engine.UserAdd(client, host, userName)
		if err != nil {
			ui.Error("Failed to add user", err.Error(), "Ensure user name is unique and sing-box is running")
			return err
		}

		if FlagJSON {
			data, _ := json.MarshalIndent(res, "", "  ")
			fmt.Println(string(data))
			return nil
		}

		ui.Success("User '%s' added successfully!", userName)
		fmt.Println()
		if res.QRCode != "" {
			fmt.Println(res.QRCode)
		}
		fmt.Println("Share Link:")
		fmt.Println(ui.SuccessStyle.Render(res.ShareLink))
		return nil
	},
}

var usersListCmd = &cobra.Command{
	Use:   "list",
	Short: "List all configured users on the server",
	RunE: func(cmd *cobra.Command, args []string) error {
		client, _, err := connectTarget()
		if err != nil {
			return err
		}
		defer client.Close()

		st, err := engine.LoadServerState(client)
		if err != nil {
			return err
		}

		if FlagJSON {
			data, _ := json.MarshalIndent(st.Users, "", "  ")
			fmt.Println(string(data))
			return nil
		}

		fmt.Printf("\nConfigured Users (%d total):\n\n", len(st.Users))
		fmt.Printf("  %-20s %-38s %s\n", "NAME", "CREDENTIAL", "CREATED AT")
		fmt.Println("  ----------------------------------------------------------------------------")
		for _, u := range st.Users {
			cred := u.UUID
			if cred == "" {
				cred = u.Password
			}
			fmt.Printf("  %-20s %-38s %s\n", u.Name, cred, u.CreatedAt.Format("2006-01-02 15:04:05"))
		}
		fmt.Println()
		return nil
	},
}

var usersRemoveCmd = &cobra.Command{
	Use:   "remove <name>",
	Short: "Remove a client identity from the server",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		userName := args[0]

		if !FlagYes {
			confirmed, err := ui.ConfirmAction("Confirm Removal", fmt.Sprintf("Remove user '%s' from the server?", userName), false)
			if err != nil || !confirmed {
				fmt.Println("Aborted.")
				return nil
			}
		}

		client, _, err := connectTarget()
		if err != nil {
			return err
		}
		defer client.Close()

		if err := engine.UserRemove(client, userName); err != nil {
			ui.Error("Failed to remove user", err.Error(), "")
			return err
		}

		ui.Success("User '%s' removed.", userName)
		return nil
	},
}

var usersShowCmd = &cobra.Command{
	Use:   "show <name>",
	Short: "Display share link and QR code for an existing user",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		userName := args[0]
		client, host, err := connectTarget()
		if err != nil {
			return err
		}
		defer client.Close()

		res, err := engine.UserShow(client, host, userName)
		if err != nil {
			return err
		}

		if FlagJSON {
			data, _ := json.MarshalIndent(res, "", "  ")
			fmt.Println(string(data))
			return nil
		}

		fmt.Printf("\nIdentity: %s (Host: %s:%d)\n\n", res.UserName, res.Host, res.Port)
		if res.QRCode != "" {
			fmt.Println(res.QRCode)
		}
		fmt.Println("Share Link:")
		fmt.Println(ui.SuccessStyle.Render(res.ShareLink))
		return nil
	},
}

func init() {
	usersCmd.AddCommand(usersAddCmd)
	usersCmd.AddCommand(usersListCmd)
	usersCmd.AddCommand(usersRemoveCmd)
	usersCmd.AddCommand(usersShowCmd)
}
