$ports = 3306,3307,3308,3310,3316,4000,4500,13306,23306
foreach ($p in $ports) {
  try {
    $l = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $p)
    $l.Start(); $l.Stop()
    Write-Host "$p : OK"
  } catch {
    Write-Host "$p : BLOCKED - $($_.Exception.Message)"
  }
}
