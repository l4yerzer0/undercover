package engine

import (
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"undercover/internal/doctor"
	"undercover/internal/executor"
	"undercover/internal/firewall"
	"undercover/internal/server"
	"undercover/internal/share"
	"undercover/internal/singbox"
	"undercover/internal/state"
	"undercover/internal/ui"
)

// LoadServerState reads and parses /etc/undercover/state.json from the target
func LoadServerState(target executor.TargetExecutor) (*state.RemoteState, error) {
	data, err := target.ReadFile("/etc/undercover/state.json")
	if err != nil {
		return nil, fmt.Errorf("undercover does not appear to be deployed on this server (state file not found): %w", err)
	}

	var st state.RemoteState
	if err := json.Unmarshal(data, &st); err != nil {
		return nil, fmt.Errorf("failed to parse remote state: %w", err)
	}
	return &st, nil
}

// SaveServerState updates /etc/undercover/state.json on the target
func SaveServerState(target executor.TargetExecutor, st *state.RemoteState) error {
	data, err := json.MarshalIndent(st, "", "  ")
	if err != nil {
		return err
	}
	return target.WriteFile("/etc/undercover/state.json", data, 0600)
}

// UserAdd creates a new user, updates the sing-box config, reloads service, and returns share link + QR
func UserAdd(target executor.TargetExecutor, host string, userName string) (*DeployResult, error) {
	st, err := LoadServerState(target)
	if err != nil {
		return nil, err
	}

	for _, u := range st.Users {
		if u.Name == userName {
			return nil, fmt.Errorf("user '%s' already exists", userName)
		}
	}

	cfgData, err := target.ReadFile("/etc/undercover/config.json")
	if err != nil {
		return nil, fmt.Errorf("failed to read /etc/undercover/config.json: %w", err)
	}

	var cfg singbox.ServerConfig
	if err := json.Unmarshal(cfgData, &cfg); err != nil {
		return nil, fmt.Errorf("failed to parse existing config: %w", err)
	}

	if len(cfg.Inbounds) == 0 {
		return nil, fmt.Errorf("invalid config: no inbounds found")
	}

	var newUUID string
	var newPassword string
	var newUser singbox.InboundUser
	var stateUser state.StateUser

	if st.Protocol == singbox.ProtocolHysteria2 {
		newPassword, err = singbox.GeneratePassword()
		if err != nil {
			return nil, err
		}
		newUser = singbox.InboundUser{
			Name:     userName,
			Password: newPassword,
		}
		stateUser = state.StateUser{
			Name:      userName,
			Password:  newPassword,
			CreatedAt: time.Now(),
		}
	} else {
		newUUID, err = singbox.GenerateUUID()
		if err != nil {
			return nil, err
		}
		newUser = singbox.InboundUser{
			Name: userName,
			UUID: newUUID,
			Flow: "xtls-rprx-vision",
		}
		stateUser = state.StateUser{
			Name:      userName,
			UUID:      newUUID,
			CreatedAt: time.Now(),
		}
	}

	cfg.Inbounds[0].Users = append(cfg.Inbounds[0].Users, newUser)

	newCfgBytes, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return nil, err
	}

	_ = target.WriteFile("/etc/undercover/config.json.bak", cfgData, 0600)

	if err := target.WriteFile("/etc/undercover/config.json", newCfgBytes, 0600); err != nil {
		return nil, err
	}

	if out, err := target.RunCombined("/usr/local/bin/sing-box check -c /etc/undercover/config.json"); err != nil {
		_ = target.WriteFile("/etc/undercover/config.json", cfgData, 0600)
		return nil, fmt.Errorf("new config validation failed (rolled back): %s (%w)", out, err)
	}

	if out, err := target.RunCombined("systemctl restart undercover.service"); err != nil {
		return nil, fmt.Errorf("failed to reload service: %s (%w)", out, err)
	}

	st.Users = append(st.Users, stateUser)
	_ = SaveServerState(target, st)

	var link string
	if st.Protocol == singbox.ProtocolHysteria2 {
		link = share.BuildHysteria2Link(share.Hysteria2Config{
			Password:   newPassword,
			Host:       host,
			Port:       st.Port,
			ServerName: st.ServerName,
			Insecure:   true,
			PinSHA256:  st.CertPinSha256,
			Alias:      userName,
		})
	} else {
		link = share.BuildVLESSRealityLink(share.VLESSRealityConfig{
			UUID:       newUUID,
			Host:       host,
			Port:       st.Port,
			PublicKey:  st.PublicKey,
			ServerName: st.ServerName,
			ShortID:    st.ShortID,
			Alias:      userName,
		})
	}
	qr, _ := share.GenerateTerminalQRCode(link)

	return &DeployResult{
		ShareLink:      link,
		QRCode:         qr,
		Host:           host,
		Port:           st.Port,
		Protocol:       st.Protocol,
		ServerName:     st.ServerName,
		SingBoxVersion: st.SingBoxVersion,
		UserName:       userName,
		UUID:           newUUID,
		Password:       newPassword,
		PublicKey:      st.PublicKey,
		ShortID:        st.ShortID,
		CertPinSha256:  st.CertPinSha256,
	}, nil
}

// UserRemove deletes a user from the configuration and reloads the service
func UserRemove(target executor.TargetExecutor, userName string) error {
	st, err := LoadServerState(target)
	if err != nil {
		return err
	}

	userFound := false
	var remainingUsers []state.StateUser
	for _, u := range st.Users {
		if u.Name == userName {
			userFound = true
		} else {
			remainingUsers = append(remainingUsers, u)
		}
	}

	if !userFound {
		return fmt.Errorf("user '%s' not found", userName)
	}

	if len(remainingUsers) == 0 {
		return fmt.Errorf("cannot remove the last remaining user (at least one user is required)")
	}

	cfgData, err := target.ReadFile("/etc/undercover/config.json")
	if err != nil {
		return err
	}

	var cfg singbox.ServerConfig
	if err := json.Unmarshal(cfgData, &cfg); err != nil {
		return err
	}

	var newUsers []singbox.InboundUser
	for _, u := range cfg.Inbounds[0].Users {
		if u.Name != userName {
			newUsers = append(newUsers, u)
		}
	}
	cfg.Inbounds[0].Users = newUsers

	newCfgBytes, _ := json.MarshalIndent(cfg, "", "  ")

	_ = target.WriteFile("/etc/undercover/config.json.bak", cfgData, 0600)
	if err := target.WriteFile("/etc/undercover/config.json", newCfgBytes, 0600); err != nil {
		return err
	}

	if out, err := target.RunCombined("/usr/local/bin/sing-box check -c /etc/undercover/config.json"); err != nil {
		_ = target.WriteFile("/etc/undercover/config.json", cfgData, 0600)
		return fmt.Errorf("validation failed: %s (%w)", out, err)
	}

	_, _ = target.RunCombined("systemctl restart undercover.service")

	st.Users = remainingUsers
	return SaveServerState(target, st)
}

// UserShow returns the share link and QR code for an existing user
func UserShow(target executor.TargetExecutor, host string, userName string) (*DeployResult, error) {
	st, err := LoadServerState(target)
	if err != nil {
		return nil, err
	}

	for _, u := range st.Users {
		if u.Name == userName {
			var link string
			if st.Protocol == singbox.ProtocolHysteria2 {
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
			qr, _ := share.GenerateTerminalQRCode(link)

			return &DeployResult{
				ShareLink:      link,
				QRCode:         qr,
				Host:           host,
				Port:           st.Port,
				Protocol:       st.Protocol,
				ServerName:     st.ServerName,
				SingBoxVersion: st.SingBoxVersion,
				UserName:       u.Name,
				UUID:           u.UUID,
				Password:       u.Password,
				PublicKey:      st.PublicKey,
				ShortID:        st.ShortID,
				CertPinSha256:  st.CertPinSha256,
			}, nil
		}
	}

	return nil, fmt.Errorf("user '%s' not found", userName)
}

// ServerStatus collects runtime status of the Undercover service
type ServerStatus struct {
	ServiceState   string               `json:"service_state"`
	Uptime         string               `json:"uptime"`
	SingBoxVersion string               `json:"sing_box_version"`
	Protocol       string               `json:"protocol"`
	Port           int                  `json:"port"`
	ServerName     string               `json:"server_name"`
	UsersCount     int                  `json:"users_count"`
	Diagnostics    []doctor.CheckResult `json:"diagnostics"`
}

func GetStatus(target executor.TargetExecutor, host string) (*ServerStatus, error) {
	st, err := LoadServerState(target)
	if err != nil {
		return nil, err
	}

	svcOut, _ := target.RunCombined("systemctl is-active undercover")
	uptimeOut, _ := target.RunCombined("systemctl show undercover --property=ActiveEnterTimestamp")

	uptimeStr := strings.TrimSpace(strings.TrimPrefix(uptimeOut, "ActiveEnterTimestamp="))
	if uptimeStr == "" {
		uptimeStr = "unknown"
	}

	diag := doctor.RunDiagnostics(target, host, st.Port, st)

	return &ServerStatus{
		ServiceState:   strings.TrimSpace(svcOut),
		Uptime:         uptimeStr,
		SingBoxVersion: st.SingBoxVersion,
		Protocol:       st.Protocol,
		Port:           st.Port,
		ServerName:     st.ServerName,
		UsersCount:     len(st.Users),
		Diagnostics:    diag,
	}, nil
}

// GetLogs returns the most recent systemd logs for the service
func GetLogs(target executor.TargetExecutor, lines int) (string, error) {
	if lines <= 0 {
		lines = 50
	}
	cmd := fmt.Sprintf("journalctl -u undercover.service -n %d --no-pager", lines)
	out, err := target.RunCombined(cmd)
	if err != nil {
		return "", fmt.Errorf("failed to fetch logs: %s (%w)", out, err)
	}
	return out, nil
}

// UpdateServer upgrades sing-box on target with automatic rollback
func UpdateServer(target executor.TargetExecutor, targetVersion string) error {
	if targetVersion == "" {
		targetVersion = singbox.DefaultSingBoxVersion
	}

	st, err := LoadServerState(target)
	if err != nil {
		return err
	}

	info, err := server.Inspect(target)
	if err != nil {
		return err
	}

	downloadURL, err := singbox.DownloadURL(targetVersion, info.Architecture)
	if err != nil {
		return err
	}
	expectedHash, err := singbox.ExpectedChecksum(targetVersion, info.Architecture)
	if err != nil {
		return err
	}

	ui.StepStatus(false, "Update", fmt.Sprintf("Updating sing-box to v%s (%s)...", targetVersion, info.Architecture))

	_, _ = target.RunCombined("cp /usr/local/bin/sing-box /usr/local/bin/sing-box.bak")

	script := fmt.Sprintf(`
set -e
curl -sSL -o /tmp/singbox_new.tar.gz "%s"
echo "%s  /tmp/singbox_new.tar.gz" | sha256sum -c -
mkdir -p /tmp/singbox_new_extract
tar -xzf /tmp/singbox_new.tar.gz -C /tmp/singbox_new_extract
find /tmp/singbox_new_extract -name sing-box -type f -exec install -m 0755 {} /usr/local/bin/sing-box \;
rm -rf /tmp/singbox_new.tar.gz /tmp/singbox_new_extract
`, downloadURL, expectedHash)

	if out, err := target.RunCombined(script); err != nil {
		_, _ = target.RunCombined("mv /usr/local/bin/sing-box.bak /usr/local/bin/sing-box || true")
		return fmt.Errorf("download or checksum failed (rolled back): %s (%w)", out, err)
	}

	if out, err := target.RunCombined("/usr/local/bin/sing-box check -c /etc/undercover/config.json"); err != nil {
		_, _ = target.RunCombined("mv /usr/local/bin/sing-box.bak /usr/local/bin/sing-box && systemctl restart undercover.service")
		return fmt.Errorf("config check failed with new binary (rolled back): %s (%w)", out, err)
	}

	if out, err := target.RunCombined("systemctl restart undercover.service"); err != nil {
		_, _ = target.RunCombined("mv /usr/local/bin/sing-box.bak /usr/local/bin/sing-box && systemctl restart undercover.service")
		return fmt.Errorf("service failed to start with new binary (rolled back): %s (%w)", out, err)
	}

	_, _ = target.RunCombined("rm -f /usr/local/bin/sing-box.bak")
	st.SingBoxVersion = targetVersion
	_ = SaveServerState(target, st)

	ui.StepStatus(true, "Update", fmt.Sprintf("sing-box updated to v%s", targetVersion))
	return nil
}

// UninstallServer removes undercover services, configs, binary and firewall rule
func UninstallServer(target executor.TargetExecutor) error {
	st, _ := LoadServerState(target)
	vpnPort := 443
	fwProto := "tcp"
	if st != nil && st.Port > 0 {
		vpnPort = st.Port
		if st.Protocol == singbox.ProtocolHysteria2 {
			fwProto = "udp"
		}
	}

	_, _ = target.RunCombined("systemctl stop undercover.service || true")
	_, _ = target.RunCombined("systemctl disable undercover.service || true")
	_, _ = target.RunCombined("rm -f /etc/systemd/system/undercover.service && systemctl daemon-reload")
	_, _ = target.RunCombined("rm -rf /etc/undercover /usr/local/bin/sing-box")
	_ = firewall.RemovePort(target, vpnPort, fwProto)

	return nil
}
