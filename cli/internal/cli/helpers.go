package cli

import (
	"fmt"
	"os"
	"runtime"

	"undercover/internal/executor"
	"undercover/internal/ssh"
	"undercover/internal/state"
	"undercover/internal/ui"
)

// resolveHost returns target host parameters
func resolveHost() (string, int, string, string, error) {
	if FlagHost != "" {
		return FlagHost, FlagSSHPort, FlagSSHUser, FlagSSHKey, nil
	}

	store, err := state.LoadLocalStore()
	if err == nil && len(store.Servers) > 0 {
		srv := store.Servers[len(store.Servers)-1]
		return srv.Host, srv.SSHPort, srv.User, srv.KeyPath, nil
	}

	return "", 0, "", "", fmt.Errorf("no server specified: provide --host flag, --local flag, or deploy a server first")
}

// connectTarget returns a TargetExecutor (Localhost or SSH)
func connectTarget() (executor.TargetExecutor, string, error) {
	isLinuxRoot := runtime.GOOS == "linux" && os.Geteuid() == 0

	// Local mode: either explicitly flagged with --local or running as root on Linux with no remote host specified
	if FlagLocal || (isLinuxRoot && FlagHost == "") {
		host := FlagHost
		if host == "" {
			host = executor.DetectPublicIP()
		}
		return executor.NewLocalExecutor(host), host, nil
	}

	// Remote SSH mode
	host, port, user, keyPath := FlagHost, FlagSSHPort, FlagSSHUser, FlagSSHKey
	var err error
	if host == "" {
		host, port, user, keyPath, err = resolveHost()
		if err != nil {
			return nil, "", err
		}
	}

	promptFunc := ui.PromptHostKey
	if FlagYes {
		promptFunc = func(h, kt, fp string) (bool, error) {
			return true, nil
		}
	}

	client, err := ssh.Dial(ssh.ClientConfig{
		Host:          host,
		Port:          port,
		User:          user,
		Password:      FlagPassword,
		KeyPath:       keyPath,
		PromptHostKey: promptFunc,
	})
	if err != nil {
		return nil, "", err
	}

	return executor.NewSSHExecutor(client, host), host, nil
}
