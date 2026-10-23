import { IonContent, IonPage } from '@ionic/react';
import React, { useEffect, useState, useCallback } from 'react';
import { App } from '@capacitor/app';
import { ScreenPinning } from '../plugins/ScreenPinning';
import './Home.css';

interface MathQuestion {
  num1: number;
  num2: number;
  answer: number;
}

const Home: React.FC = () => {
  // Mode: 'idle' (setting alarm) | 'ringing' (alarm active + math challenge) | 'solved' (unpinned)
  const [mode, setMode] = useState<'idle' | 'ringing' | 'solved'>('idle');
  const [permissions, setPermissions] = useState<{
    pinningEnabled: boolean;
    canFullScreen: boolean;
    canOverlay: boolean;
    isDeviceOwner?: boolean;
    isLockTaskPermitted?: boolean;
  }>({
    pinningEnabled: false,
    canFullScreen: true,
    canOverlay: true,
    isDeviceOwner: false,
    isLockTaskPermitted: false,
  });
  const [savedAlarmTime, setSavedAlarmTime] = useState<string | null>(null);
  const [alarmHour, setAlarmHour] = useState<string>('07');
  const [alarmMinute, setAlarmMinute] = useState<string>('00');
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  // Math Challenge State
  const [question, setQuestion] = useState<MathQuestion>({ num1: 1, num2: 1, answer: 2 });
  const [userAnswer, setUserAnswer] = useState<string>('');
  const [errorMessage, setErrorMessage] = useState<string>('');

  // Periksa kesiapan seluruh izin yang dibutuhkan di Android
  const checkAllPermissions = useCallback(async () => {
    try {
      const res = await ScreenPinning.checkPermissions();
      setPermissions(res);
    } catch (e) {
      console.warn('Gagal cek status izin:', e);
    }
  }, []);

  // Generate soal matematika acak
  const generateNewQuestion = useCallback(() => {
    const n1 = Math.floor(Math.random() * 20) + 5; // 5 - 25
    const n2 = Math.floor(Math.random() * 20) + 5; // 5 - 25
    setQuestion({ num1: n1, num2: n2, answer: n1 + n2 });
    setUserAnswer('');
    setErrorMessage('');
  }, []);

  // Mulai mode alarm berbunyi & aktifkan pinning layar
  const triggerAlarmMode = useCallback(async () => {
    setMode('ringing');
    generateNewQuestion();
    try {
      await ScreenPinning.startPinning();
    } catch (e) {
      console.error('Error saat startPinning:', e);
    }
  }, [generateNewQuestion]);

  useEffect(() => {
    checkAllPermissions();

    // Periksa apakah aplikasi dibuka langsung oleh pemicu alarm
    ScreenPinning.checkAlarmState().then((state) => {
      if (state.isAlarmActive) {
        triggerAlarmMode();
      }
    });

    // Dengarkan event broadcast jika alarm berbunyi saat app sedang terbuka di foreground
    const listenerPromise = ScreenPinning.addListener('onAlarmTriggered', (data) => {
      if (data.isAlarmActive) {
        triggerAlarmMode();
      }
    });

    // Periksa status alarm saat aplikasi kembali ke foreground (resume)
    const resumePromise = App.addListener('appStateChange', (state) => {
      if (state.isActive) {
        ScreenPinning.checkAlarmState().then((s) => {
          if (s.isAlarmActive) {
            triggerAlarmMode();
          }
        });
      }
    });

    return () => {
      listenerPromise.then((handle) => handle.remove()).catch(() => { });
      resumePromise.then((handle) => handle.remove()).catch(() => { });
    };
  }, [checkAllPermissions, triggerAlarmMode]);

  // Pasang Alarm berdasarkan Jam & Menit
  const handleSaveAlarm = async () => {
    const now = new Date();
    const target = new Date();
    target.setHours(parseInt(alarmHour, 10), parseInt(alarmMinute, 10), 0, 0);

    // Jika waktu sudah lewat hari ini, pasang untuk besok
    if (target.getTime() <= now.getTime()) {
      target.setDate(target.getDate() + 1);
    }

    try {
      await ScreenPinning.setAlarm({ timestamp: target.getTime() });
      const timeStr = target.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      setSavedAlarmTime(timeStr);
      setToastMessage(`✓ Alarm dipasang untuk jam ${timeStr}. Anda bisa menutup aplikasi sekarang.`);
    } catch (e: any) {
      setToastMessage(`⚠️ Gagal pasang alarm: ${e.message || e}`);
    }
  };

  // Uji Coba Cepat (10 Detik)
  const handleQuickTest = async () => {
    try {
      await ScreenPinning.setAlarm({ delaySeconds: 10 });
      const testTime = new Date(Date.now() + 10000).toLocaleTimeString();
      setSavedAlarmTime(`10 detik lagi (${testTime})`);
      setToastMessage('✓ Alarm 10 detik aktif! Silakan minimize/tutup aplikasi sekarang.');
    } catch (e: any) {
      setToastMessage(`⚠️ Gagal pasang alarm uji coba: ${e.message || e}`);
    }
  };

  // Batalkan Alarm
  const handleCancelAlarm = async () => {
    try {
      await ScreenPinning.cancelAlarm();
      setSavedAlarmTime(null);
      setToastMessage('✓ Alarm berhasil dibatalkan.');
    } catch (e: any) {
      setToastMessage(`⚠️ Gagal membatalkan: ${e.message || e}`);
    }
  };

  // Submit Jawaban Matematika
  const handleSubmitAnswer = async () => {
    const parsed = parseInt(userAnswer, 10);
    if (isNaN(parsed)) {
      setErrorMessage('Masukkan angka terlebih dahulu!');
      return;
    }

    if (parsed === question.answer) {
      try {
        // Hentikan suara dan unpin layar secara otomatis
        await ScreenPinning.stopPinning();
        setMode('solved');
      } catch (e) {
        console.error('Error saat stopPinning:', e);
        setMode('solved');
      }
    } else {
      setErrorMessage('Jawaban salah! Alarm tetap berbunyi, coba lagi.');
      setUserAnswer('');
    }
  };

  const handleKeypadPress = (val: string) => {
    if (val === 'C') {
      setUserAnswer('');
      setErrorMessage('');
    } else if (val === 'DEL') {
      setUserAnswer((prev) => prev.slice(0, -1));
    } else {
      setUserAnswer((prev) => (prev.length < 5 ? prev + val : prev));
      setErrorMessage('');
    }
  };

  return (
    <IonPage>
      <IonContent fullscreen className="ion-padding">
        {mode === 'idle' && (
          <div className="alarm-container">
            <div className="header-section">
              <h1>⏰ Math Alarm & Pinning</h1>
              <p>Alarm tantangan matematika dengan mode sematan layar (screen pinning)</p>
            </div>

            {/* Status Kesiapan Fitur Android */}
            <div className="card">
              <h3 style={{ margin: '0 0 10px 0', fontSize: '1.05rem', fontWeight: 700 }}>Status Izin Sistem</h3>

              {/* 1. App Pinning */}
              <div style={{ marginBottom: '12px' }}>
                <span className={`status-badge ${permissions.pinningEnabled ? 'ready' : 'warning'}`}>
                  {permissions.pinningEnabled ? '✓ Mode Pinning: Aktif' : '⚠️ Mode Pinning: Belum Aktif'}
                </span>
                {!permissions.pinningEnabled && (
                  <p style={{ fontSize: '0.85rem', color: '#64748b', margin: '4px 0 6px 0' }}>
                    Agar layar terkunci saat alarm berbunyi, aktifkan <b>App pinning</b> di pengaturan.
                    <button className="secondary-btn" style={{ padding: '8px 12px', marginTop: '6px' }} onClick={() => ScreenPinning.openScreenPinningSettings()}>
                      Buka Pengaturan Keamanan
                    </button>
                  </p>
                )}
              </div>

              {/* 2. Full-Screen Intent (Wajib untuk membuka layar saat app di-close di Android 14+) */}
              <div>
                <span className={`status-badge ${permissions.canFullScreen ? 'ready' : 'warning'}`}>
                  {permissions.canFullScreen ? '✓ Izin Layar Penuh: Aktif' : '⚠️ Izin Layar Penuh: Diperlukan'}
                </span>
                {!permissions.canFullScreen && (
                  <div style={{ marginTop: '4px' }}>
                    <p style={{ fontSize: '0.85rem', color: '#dc2626', margin: '4px 0 6px 0', fontWeight: 600 }}>
                      Wajib aktif agar layar alarm otomatis muncul saat aplikasi tertutup total!
                    </p>
                    <button className="secondary-btn" style={{ padding: '8px 12px', borderColor: '#f87171' }} onClick={() => ScreenPinning.requestFullScreenPermission()}>
                      Izinkan Notifikasi Layar Penuh
                    </button>
                  </div>
                )}
              </div>

              {/* 3. Tampil di Atas Aplikasi Lain (Display over other apps) */}
              <div style={{ marginTop: '12px' }}>
                <span className={`status-badge ${permissions.canOverlay ? 'ready' : 'warning'}`}>
                  {permissions.canOverlay ? '✓ Tampil di Atas Aplikasi: Aktif' : '⚠️ Tampil di Atas Aplikasi: Disarankan'}
                </span>
                {!permissions.canOverlay && (
                  <div style={{ marginTop: '4px' }}>
                    <p style={{ fontSize: '0.85rem', color: '#64748b', margin: '4px 0 6px 0' }}>
                      Izinkan aplikasi tampil di atas aplikasi lain agar layar kuis alarm langsung terbuka seketika.
                    </p>
                    <button className="secondary-btn" style={{ padding: '8px 12px' }} onClick={() => ScreenPinning.requestOverlayPermission()}>
                      Izinkan Tampil di Atas Aplikasi
                    </button>
                  </div>
                )}
              </div>

              {/* 4. Pinning Langsung Tanpa Dialog Got It (Mode Kiosk / Device Owner) */}
              <div style={{ marginTop: '12px' }}>
                <span className={`status-badge ${permissions.isLockTaskPermitted || permissions.isDeviceOwner ? 'ready' : 'warning'}`}>
                  {permissions.isLockTaskPermitted || permissions.isDeviceOwner
                    ? '✓ Pinning Langsung (Tanpa Got It): Aktif'
                    : 'ℹ️ Pinning Langsung (Tanpa Got It): Butuh Perintah ADB'}
                </span>
                {!(permissions.isLockTaskPermitted || permissions.isDeviceOwner) && (
                  <div style={{ marginTop: '4px', background: '#f8fafc', padding: '10px', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                    <p style={{ fontSize: '0.82rem', color: '#475569', margin: '0 0 6px 0', lineHeight: 1.4 }}>
                      Agar Android langsung mem-pin layar <b>tanpa menampilkan tombol konfirmasi "Got It"</b>, sistem Android mewajibkan hak Device Owner via ADB:
                    </p>
                    <code style={{ fontSize: '0.75rem', background: '#e2e8f0', padding: '4px 6px', borderRadius: '4px', display: 'block', wordBreak: 'break-all' }}>
                      adb shell dpm set-device-owner io.ionic.starter/.AdminReceiver
                    </code>
                    <button
                      className="secondary-btn"
                      style={{ padding: '6px 10px', marginTop: '8px', fontSize: '0.8rem' }}
                      onClick={async () => {
                        await ScreenPinning.enableKioskMode();
                        checkAllPermissions();
                      }}
                    >
                      Periksa Ulang Status Kiosk
                    </button>
                  </div>
                )}
              </div>
            </div>

            {/* Pengaturan Waktu Alarm */}
            <div className="card">
              <h3 style={{ margin: '0 0 12px 0', fontSize: '1.15rem' }}>Atur Waktu Alarm</h3>
              <div className="time-picker-box">
                <input
                  type="number"
                  min="0"
                  max="23"
                  className="time-input"
                  value={alarmHour}
                  onChange={(e) => {
                    const val = Math.min(23, Math.max(0, parseInt(e.target.value) || 0));
                    setAlarmHour(val.toString().padStart(2, '0'));
                  }}
                />
                <span style={{ fontSize: '2rem', fontWeight: 800 }}>:</span>
                <input
                  type="number"
                  min="0"
                  max="59"
                  className="time-input"
                  value={alarmMinute}
                  onChange={(e) => {
                    const val = Math.min(59, Math.max(0, parseInt(e.target.value) || 0));
                    setAlarmMinute(val.toString().padStart(2, '0'));
                  }}
                />
              </div>

              <button className="primary-btn" onClick={handleSaveAlarm}>
                💾 Simpan & Pasang Alarm
              </button>

              <button className="secondary-btn" onClick={handleQuickTest}>
                ⚡ Uji Coba Cepat (Bunyi 10 Detik Lagi)
              </button>

              {toastMessage && (
                <div style={{
                  background: '#f0fdf4',
                  border: '1px solid #86efac',
                  color: '#166534',
                  padding: '12px 14px',
                  borderRadius: '10px',
                  marginTop: '12px',
                  fontSize: '0.9rem',
                  fontWeight: 500,
                  textAlign: 'center'
                }}>
                  {toastMessage}
                </div>
              )}

              {savedAlarmTime && (
                <div style={{ marginTop: '16px', textAlign: 'center' }}>
                  <p style={{ color: '#059669', fontWeight: 600, margin: '8px 0' }}>
                    Alarm aktif untuk: {savedAlarmTime}
                  </p>
                  <p style={{ fontSize: '0.85rem', color: '#64748b', margin: '0 0 10px 0' }}>
                    Anda bisa menutup aplikasi sekarang. Layar akan otomatis menyala dan masuk mode pinning saat alarm berbunyi.
                  </p>
                  <button className="danger-btn" onClick={handleCancelAlarm}>
                    Batalkan Alarm
                  </button>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Mode Alarm Berbunyi + Math Challenge */}
        {mode === 'ringing' && (
          <div className="ringing-mode">
            <div className="ringing-card">
              <div className="alarm-icon">⏰</div>
              <h2 style={{ color: '#dc2626', margin: '0 0 6px 0', fontSize: '1.5rem', fontWeight: 800 }}>
                ALARM BERBUNYI!
              </h2>
              <p style={{ color: '#6b7280', fontSize: '0.9rem', margin: '0 0 16px 0' }}>
                Layar dalam mode pinning. Selesaikan kuis untuk membuka kuncian dan mematikan alarm.
              </p>

              {/* Soal Matematika */}
              <div className="math-equation">
                {question.num1} + {question.num2} = ?
              </div>

              {/* Tampilan Jawaban */}
              <div className="math-input-display">
                {userAnswer || <span style={{ color: '#94a3b8' }}>_</span>}
              </div>

              {errorMessage && (
                <p style={{ color: '#dc2626', fontWeight: 600, fontSize: '0.9rem', margin: '0 0 10px 0' }}>
                  {errorMessage}
                </p>
              )}

              {/* Keypad Angka */}
              <div className="keypad">
                {['1', '2', '3', '4', '5', '6', '7', '8', '9', 'C', '0', 'DEL'].map((btn) => (
                  <button
                    key={btn}
                    className={`keypad-btn ${btn === 'C' || btn === 'DEL' ? 'action' : ''}`}
                    onClick={() => handleKeypadPress(btn)}
                  >
                    {btn}
                  </button>
                ))}
              </div>

              <button className="primary-btn" style={{ background: '#dc2626' }} onClick={handleSubmitAnswer}>
                ✓ Kirim Jawaban & Matikan Alarm
              </button>
            </div>
          </div>
        )}

        {/* Mode Selesai / Berhasil Dijawab */}
        {mode === 'solved' && (
          <div className="alarm-container" style={{ justifyContent: 'center' }}>
            <div className="card success-box">
              <div style={{ fontSize: '3rem', marginBottom: '10px' }}>🎉</div>
              <h2 style={{ color: '#03543f', margin: '0 0 8px 0', fontWeight: 800 }}>
                Jawaban Benar!
              </h2>
              <p style={{ color: '#046c4e', fontSize: '0.95rem', margin: '0 0 16px 0' }}>
                Alarm telah dihentikan dan layar berhasil di-unpin. Selamat beraktivitas!
              </p>
              <button
                className="primary-btn"
                onClick={() => {
                  setMode('idle');
                  setSavedAlarmTime(null);
                  checkAllPermissions();
                }}
              >
                Kembali ke Menu Utama
              </button>
            </div>
          </div>
        )}
      </IonContent>
    </IonPage>
  );
};

export default Home;
