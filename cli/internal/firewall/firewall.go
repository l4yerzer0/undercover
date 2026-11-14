package firewall

import (
	"fmt"
	"strings"

	"undercover/internal/executor"
)

type FirewallType string

const (
	TypeUFW      FirewallType = "ufw"
	TypeNFTables FirewallType = "nftables"
	TypeIPTables FirewallType = "iptables"
	TypeNone     FirewallType = "none"
)

type FirewallInfo struct {
	Type   FirewallType
	Active bool
}

// Detect checks which firewall system is currently active on the server
func Detect(exec executor.TargetExecutor) (*FirewallInfo, error) {
	// 1. Check UFW
	ufwOut, _ := exec.RunCombined("ufw status 2>/dev/null")
	if strings.Contains(ufwOut, "Status: active") {
		return &FirewallInfo{Type: TypeUFW, Active: true}, nil
	}

	// 2. Check nftables
	nftOut, _ := exec.RunCombined("nft list ruleset 2>/dev/null")
	if len(strings.TrimSpace(nftOut)) > 0 && strings.Contains(nftOut, "table") {
		return &FirewallInfo{Type: TypeNFTables, Active: true}, nil
	}

	// 3. Check iptables
	iptOut, _ := exec.RunCombined("iptables -L -n 2>/dev/null")
	if strings.Contains(iptOut, "Chain INPUT") {
		// Count custom rules
		lines := strings.Split(iptOut, "\n")
		ruleCount := 0
		for _, l := range lines {
			l = strings.TrimSpace(l)
			if l != "" && !strings.HasPrefix(l, "Chain") && !strings.HasPrefix(l, "target") {
				ruleCount++
			}
		}
		if ruleCount > 0 {
			return &FirewallInfo{Type: TypeIPTables, Active: true}, nil
		}
	}

	return &FirewallInfo{Type: TypeNone, Active: false}, nil
}

// AllowPort adds a non-destructive rule for the VPN port to the active firewall (default proto: tcp, or "udp", "both")
func AllowPort(exec executor.TargetExecutor, port int, proto ...string) error {
	protocol := "tcp"
	if len(proto) > 0 && proto[0] != "" {
		protocol = proto[0]
	}

	info, err := Detect(exec)
	if err != nil {
		return err
	}

	if !info.Active {
		// Do not enable an inactive firewall without user request
		return nil
	}

	protocols := []string{protocol}
	if protocol == "both" {
		protocols = []string{"tcp", "udp"}
	}

	for _, p := range protocols {
		switch info.Type {
		case TypeUFW:
			cmd := fmt.Sprintf("ufw allow %d/%s comment 'undercover-vpn'", port, p)
			if out, err := exec.RunCombined(cmd); err != nil {
				return fmt.Errorf("failed to add UFW rule for port %d/%s: %s (%w)", port, p, out, err)
			}
		case TypeNFTables:
			cmd := fmt.Sprintf("nft add rule inet filter input %s dport %d accept 2>/dev/null || nft add rule ip filter INPUT %s dport %d accept 2>/dev/null || true", p, port, p, port)
			_, _ = exec.RunCombined(cmd)
		case TypeIPTables:
			cmd := fmt.Sprintf("iptables -C INPUT -p %s --dport %d -j ACCEPT 2>/dev/null || iptables -I INPUT -p %s --dport %d -m comment --comment 'undercover-vpn' -j ACCEPT", p, port, p, port)
			if out, err := exec.RunCombined(cmd); err != nil {
				return fmt.Errorf("failed to add iptables rule for port %d/%s: %s (%w)", port, p, out, err)
			}
		}
	}

	return nil
}

// RemovePort deletes only the rule added for Undercover VPN
func RemovePort(exec executor.TargetExecutor, port int, proto ...string) error {
	protocol := "tcp"
	if len(proto) > 0 && proto[0] != "" {
		protocol = proto[0]
	}

	info, err := Detect(exec)
	if err != nil {
		return err
	}

	if !info.Active {
		return nil
	}

	protocols := []string{protocol}
	if protocol == "both" {
		protocols = []string{"tcp", "udp"}
	}

	for _, p := range protocols {
		switch info.Type {
		case TypeUFW:
			cmd := fmt.Sprintf("ufw delete allow %d/%s 2>/dev/null || true", port, p)
			_, _ = exec.RunCombined(cmd)
		case TypeIPTables:
			cmd := fmt.Sprintf("iptables -D INPUT -p %s --dport %d -m comment --comment 'undercover-vpn' -j ACCEPT 2>/dev/null || true", p, port)
			_, _ = exec.RunCombined(cmd)
		case TypeNFTables:
			cmd := fmt.Sprintf("nft delete rule inet filter input %s dport %d accept 2>/dev/null || true", p, port)
			_, _ = exec.RunCombined(cmd)
		}
	}

	return nil
}
