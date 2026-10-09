import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api";
import { ThemeProvider } from "@/lib/theme";
import LoginPage from "./LoginPage";

const login = vi.fn();
vi.mock("@/lib/auth", () => ({ useAuth: () => ({ login }) }));

function renderLogin() {
  render(
    <ThemeProvider>
    <MemoryRouter initialEntries={["/login"]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/dashboard" element={<p>dashboard page</p>} />
        <Route path="/onboarding" element={<p>onboarding page</p>} />
      </Routes>
    </MemoryRouter>
    </ThemeProvider>,
  );
}

describe("LoginPage", () => {
  beforeEach(() => {
    login.mockReset();
  });

  it("validates before calling the server", async () => {
    renderLogin();
    await userEvent.type(screen.getByLabelText("Email"), "not-an-email");
    await userEvent.click(screen.getByRole("button", { name: /log in/i }));
    expect(await screen.findByText("Enter a valid email")).toBeInTheDocument();
    expect(screen.getByText("Enter your password")).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it("goes to the dashboard after login", async () => {
    login.mockResolvedValue({ onboardingCompleted: true });
    renderLogin();
    await userEvent.type(screen.getByLabelText("Email"), "hari@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "secret123");
    await userEvent.click(screen.getByRole("button", { name: /log in/i }));
    expect(await screen.findByText("dashboard page")).toBeInTheDocument();
    expect(login).toHaveBeenCalledWith("hari@example.com", "secret123");
  });

  it("sends new users to onboarding", async () => {
    login.mockResolvedValue({ onboardingCompleted: false });
    renderLogin();
    await userEvent.type(screen.getByLabelText("Email"), "new@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "secret123");
    await userEvent.click(screen.getByRole("button", { name: /log in/i }));
    expect(await screen.findByText("onboarding page")).toBeInTheDocument();
  });

  it("shows the server's error", async () => {
    login.mockRejectedValue(new ApiError(401, { message: "Email or password is incorrect" }));
    renderLogin();
    await userEvent.type(screen.getByLabelText("Email"), "hari@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "wrong");
    await userEvent.click(screen.getByRole("button", { name: /log in/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Email or password is incorrect");
  });
});
