import asyncio
import threading
import tkinter as tk
from tkinter import messagebox, font
import websockets
import json
from pynput.mouse import Controller, Button


class RemoteControlServer:
    def __init__(self, password, log_callback):
        self.password = password.strip()
        self.log_callback = log_callback
        self.server = None
        self.loop = None
        self.running = False
        self.mouse = Controller()  # pynput mouse controller

    async def _handler(self, websocket):
        try:
            # Ask for password only if one is set
            if self.password:
                await websocket.send("Enter password:")
                client_pass = await websocket.recv()

                if client_pass != self.password:
                    await websocket.send("Authentication failed ❌")
                    await websocket.close()
                    return

                await websocket.send("Authentication successful ✅")
                self.log_callback("Client connected (authenticated)")

            else:
                await websocket.send("Connected (no password required)")
                self.log_callback("Client connected (no password)")

            # Message loop
            async for message in websocket:
                try:
                    data = json.loads(message)

                    if data["type"] == "MOVE":
                        dx, dy = data["dx"], data["dy"]
                        # Get current position
                        current_x, current_y = self.mouse.position
                        # Move to new position (instant, no duration parameter needed)
                        self.mouse.position = (current_x + dx * 0.8, current_y + dy * 0.8)

                    elif data["type"] == "CLICK":
                        self.mouse.click(Button.left, 1)

                except Exception as e:
                    self.log_callback(f"Error parsing/acting on message: {e}")

                await websocket.send(f"Echo: {message}")

        except websockets.exceptions.ConnectionClosed:
            self.log_callback("Client disconnected ❌")

    async def _run_server(self, host, port):
        self.server = await websockets.serve(self._handler, host, port)
        self.log_callback(f"Server started on ws://{host}:{port}")
        await self.server.wait_closed()

    def start(self, host="0.0.0.0", port=5000):
        if self.running:
            self.log_callback("Server already running!")
            return

        def run():
            self.loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self.loop)
            self.loop.run_until_complete(self._run_server(host, port))

        self.running = True
        threading.Thread(target=run, daemon=True).start()

    def stop(self):
        if self.server:
            self.loop.call_soon_threadsafe(self.server.close)
            self.log_callback("Server stopped")
        self.running = False


# ---------------- Modern UI ---------------- #
class ModernButton(tk.Button):
    def __init__(self, parent, text, command=None, bg_color="#007AFF", hover_color="#0056CC", 
                 text_color="white", disabled_color="#E5E5E7", **kwargs):
        super().__init__(parent, text=text, command=command, **kwargs)
        
        self.bg_color = bg_color
        self.hover_color = hover_color
        self.text_color = text_color
        self.disabled_color = disabled_color
        
        self.configure(
            bg=bg_color,
            fg=text_color,
            relief="flat",
            borderwidth=0,
            cursor="hand2",
            font=("SF Pro Display", 14, "normal"),
            padx=30,
            pady=12
        )
        
        self.bind("<Enter>", self._on_hover)
        self.bind("<Leave>", self._on_leave)
        
    def _on_hover(self, event):
        if self["state"] != "disabled":
            self.configure(bg=self.hover_color)
            
    def _on_leave(self, event):
        if self["state"] != "disabled":
            self.configure(bg=self.bg_color)
    
    def config(self, **kwargs):
        if "state" in kwargs:
            if kwargs["state"] == "disabled":
                super().configure(bg=self.disabled_color, fg="#8E8E93")
            else:
                super().configure(bg=self.bg_color, fg=self.text_color)
        super().configure(**kwargs)


class ModernEntry(tk.Entry):
    def __init__(self, parent, placeholder="", **kwargs):
        super().__init__(parent, **kwargs)
        
        self.placeholder = placeholder
        self.placeholder_color = "#8E8E93"
        self.normal_color = "#000000"
        
        self.configure(
            font=("SF Pro Display", 14),
            bg="#F2F2F7",
            fg=self.placeholder_color,
            relief="flat",
            borderwidth=0,
            insertbackground="#007AFF",
            selectbackground="#007AFF",
            selectforeground="white"
        )
        
        if placeholder:
            self.insert(0, placeholder)
            self.bind("<FocusIn>", self._clear_placeholder)
            self.bind("<FocusOut>", self._add_placeholder)
    
    def _clear_placeholder(self, event):
        if self.get() == self.placeholder:
            self.delete(0, tk.END)
            self.configure(fg=self.normal_color)
    
    def _add_placeholder(self, event):
        if not self.get():
            self.insert(0, self.placeholder)
            self.configure(fg=self.placeholder_color)
    
    def get_actual_value(self):
        value = self.get()
        return "" if value == self.placeholder else value


class ModernText(tk.Text):
    def __init__(self, parent, **kwargs):
        super().__init__(parent, **kwargs)
        
        self.configure(
            font=("SF Mono", 12),
            bg="#1C1C1E",
            fg="#FFFFFF",
            relief="flat",
            borderwidth=0,
            insertbackground="#007AFF",
            selectbackground="#007AFF",
            selectforeground="white",
            wrap="word",
            padx=15,
            pady=15
        )


class RemoteControlUI:
    def __init__(self, root):
        self.root = root
        self.root.title("Remote Control Server")
        self.root.geometry("500x700")
        self.root.configure(bg="#FFFFFF")
        self.root.resizable(True, True)
        
        # Configure custom fonts
        try:
            self.title_font = font.Font(family="SF Pro Display", size=28, weight="bold")
            self.subtitle_font = font.Font(family="SF Pro Display", size=16, weight="normal")
            self.body_font = font.Font(family="SF Pro Display", size=14, weight="normal")
        except:
            # Fallback fonts for systems without SF Pro
            self.title_font = font.Font(family="Helvetica", size=28, weight="bold")
            self.subtitle_font = font.Font(family="Helvetica", size=16, weight="normal")
            self.body_font = font.Font(family="Helvetica", size=14, weight="normal")
        
        self.server = None
        self.setup_ui()
        
        # Add some breathing room
        self.root.update_idletasks()
        
    def setup_ui(self):
        # Main container with padding
        main_frame = tk.Frame(self.root, bg="#FFFFFF")
        main_frame.pack(fill="both", expand=True, padx=30, pady=30)
        
        # Header section
        header_frame = tk.Frame(main_frame, bg="#FFFFFF")
        header_frame.pack(fill="x", pady=(0, 30))
        
        title_label = tk.Label(
            header_frame,
            text="Remote Control",
            font=self.title_font,
            bg="#FFFFFF",
            fg="#000000"
        )
        title_label.pack(anchor="w")
        
        subtitle_label = tk.Label(
            header_frame,
            text="Secure WebSocket Server",
            font=self.subtitle_font,
            bg="#FFFFFF",
            fg="#8E8E93"
        )
        subtitle_label.pack(anchor="w", pady=(5, 0))
        
        # Password section
        password_frame = tk.Frame(main_frame, bg="#FFFFFF")
        password_frame.pack(fill="x", pady=(0, 30))
        
        password_label = tk.Label(
            password_frame,
            text="Security",
            font=self.body_font,
            bg="#FFFFFF",
            fg="#000000"
        )
        password_label.pack(anchor="w", pady=(0, 8))
        
        # Password input container
        password_container = tk.Frame(password_frame, bg="#F2F2F7", height=50)
        password_container.pack(fill="x", pady=(0, 5))
        password_container.pack_propagate(False)
        
        self.password_entry = ModernEntry(
            password_container,
            placeholder="Enter password (optional)",
            show="•"
        )
        self.password_entry.pack(fill="both", expand=True, padx=15, pady=12)
        
        password_help = tk.Label(
            password_frame,
            text="Leave empty for no authentication, or use 6+ characters",
            font=("SF Pro Display", 12),
            bg="#FFFFFF",
            fg="#8E8E93"
        )
        password_help.pack(anchor="w")
        
        # Control buttons section
        controls_frame = tk.Frame(main_frame, bg="#FFFFFF")
        controls_frame.pack(fill="x", pady=(0, 30))
        
        controls_label = tk.Label(
            controls_frame,
            text="Server Control",
            font=self.body_font,
            bg="#FFFFFF",
            fg="#000000"
        )
        controls_label.pack(anchor="w", pady=(0, 15))
        
        buttons_frame = tk.Frame(controls_frame, bg="#FFFFFF")
        buttons_frame.pack(fill="x")
        
        self.start_btn = ModernButton(
            buttons_frame,
            text="▶ Start Server",
            command=self.start_server,
            bg_color="#34C759",
            hover_color="#28A745"
        )
        self.start_btn.pack(fill="x", pady=(0, 10))
        
        self.stop_btn = ModernButton(
            buttons_frame,
            text="⏹ Stop Server",
            command=self.stop_server,
            bg_color="#FF3B30",
            hover_color="#D70015"
        )
        self.stop_btn.pack(fill="x")
        self.stop_btn.config(state="disabled")
        
        # Server status
        self.status_frame = tk.Frame(main_frame, bg="#FFFFFF")
        self.status_frame.pack(fill="x", pady=(20, 0))
        
        self.status_label = tk.Label(
            self.status_frame,
            text="● Offline",
            font=("SF Pro Display", 14, "bold"),
            bg="#FFFFFF",
            fg="#8E8E93"
        )
        self.status_label.pack(anchor="w")
        
        # Log section
        log_frame = tk.Frame(main_frame, bg="#FFFFFF")
        log_frame.pack(fill="both", expand=True, pady=(20, 0))
        
        log_label = tk.Label(
            log_frame,
            text="Activity Log",
            font=self.body_font,
            bg="#FFFFFF",
            fg="#000000"
        )
        log_label.pack(anchor="w", pady=(0, 10))
        
        # Log container with rounded corners effect
        log_container = tk.Frame(log_frame, bg="#1C1C1E", relief="flat", borderwidth=0)
        log_container.pack(fill="both", expand=True)
        
        self.log_box = ModernText(log_container, state="disabled")
        self.log_box.pack(fill="both", expand=True)
        
        # Initial log message
        self.log("Welcome to Remote Control Server")
        self.log("Configure your settings and start the server")

    def log(self, msg):
        self.log_box.config(state="normal")
        self.log_box.insert(tk.END, f"• {msg}\n")
        self.log_box.config(state="disabled")
        self.log_box.see(tk.END)

    def update_status(self, status, color):
        self.status_label.config(text=f"● {status}", fg=color)

    def start_server(self):
        password = self.password_entry.get_actual_value()
        if password and len(password) < 6:
            messagebox.showerror(
                "Invalid Password", 
                "Password must be at least 6 characters long, or leave empty for no authentication."
            )
            return

        self.server = RemoteControlServer(password, self.log)
        self.server.start()
        
        self.start_btn.config(state="disabled")
        self.stop_btn.config(state="normal")
        self.update_status("Starting...", "#FF9500")
        
        # Update status after a short delay
        self.root.after(1000, lambda: self.update_status("Online", "#34C759"))
        
        self.log("Server initialization complete")

    def stop_server(self):
        if self.server:
            self.server.stop()
            
        self.start_btn.config(state="normal")
        self.stop_btn.config(state="disabled")
        self.update_status("Offline", "#8E8E93")


if __name__ == "__main__":
    root = tk.Tk()
    
    # Set window icon and properties for modern look
    try:
        # Try to set a modern window style (works on some systems)
        root.tk.call('tk', 'scaling', 1.2)  # Better scaling for high-DPI displays
    except:
        pass
    
    app = RemoteControlUI(root)
    root.mainloop()