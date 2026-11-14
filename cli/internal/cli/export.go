package cli

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/spf13/cobra"
	"undercover/internal/engine"
	"undercover/internal/share"
	"undercover/internal/ui"
)

var flagExportOut string

type ExportItem struct {
	Name      string `json:"name"`
	UUID      string `json:"uuid,omitempty"`
	Password  string `json:"password,omitempty"`
	ShareLink string `json:"share_link"`
}

type ExportResult struct {
	Host       string       `json:"host"`
	Port       int          `json:"port"`
	Protocol   string       `json:"protocol"`
	ServerName string       `json:"server_name"`
	Users      []ExportItem `json:"users"`
}

var exportCmd = &cobra.Command{
	Use:   "export",
	Short: "Export all client identities and share links to a file or stdout",
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

		exportData := ExportResult{
			Host:       host,
			Port:       st.Port,
			Protocol:   st.Protocol,
			ServerName: st.ServerName,
			Users:      make([]ExportItem, 0, len(st.Users)),
		}

		for _, u := range st.Users {
			var link string
			if st.Protocol == "hysteria2" {
				link = share.BuildHysteria2Link(share.Hysteria2Config{
					Password:   u.Password,
					Host:       host,
					Port:       st.Port,
					ServerName: st.ServerName,
					Insecure:   true,
					PinSHA256:  st.CertPinSha256,
					Alias:      u.Name,
				})
			} else {
				link = share.BuildVLESSRealityLink(share.VLESSRealityConfig{
					UUID:       u.UUID,
					Host:       host,
					Port:       st.Port,
					PublicKey:  st.PublicKey,
					ServerName: st.ServerName,
					ShortID:    st.ShortID,
					Alias:      u.Name,
				})
			}
			exportData.Users = append(exportData.Users, ExportItem{
				Name:      u.Name,
				UUID:      u.UUID,
				Password:  u.Password,
				ShareLink: link,
			})
		}

		outJSON, err := json.MarshalIndent(exportData, "", "  ")
		if err != nil {
			return err
		}

		if flagExportOut != "" {
			if err := os.WriteFile(flagExportOut, outJSON, 0600); err != nil {
				return fmt.Errorf("failed to write export file: %w", err)
			}
			ui.Success("Exported %d client identities to %s", len(exportData.Users), flagExportOut)
		} else {
			fmt.Println(string(outJSON))
		}

		return nil
	},
}

func init() {
	exportCmd.Flags().StringVarP(&flagExportOut, "output", "o", "", "Destination file path (prints to stdout if omitted)")
}
