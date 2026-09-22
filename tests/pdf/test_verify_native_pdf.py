"""The verifier must reject omission, duplication and changed lifecycle data."""
import unittest
from verify_native_pdf import verify_text


class VerifyNativePdfTest(unittest.TestCase):
    row = {"cells": ["1", "Siswa Contoh 1", "DEMO-1", "4", "2", "0", "0", "5", "0.00"],
           "details": ["Lengkap: 1 | Parsial: 2 | Legacy: 3 | Perlu tinjauan: 4", "Sesi Larkam: 0"]}
    text = "\n".join(row["cells"] + row["details"])

    def test_complete_row(self):
        verify_text(self.text, [self.row])

    def test_missing_student(self):
        with self.assertRaises(ValueError):
            verify_text("Total Siswa: 1", [self.row])

    def test_duplicate_student(self):
        with self.assertRaises(ValueError):
            verify_text(self.text + "\n" + self.text, [self.row])

    def test_changed_lifecycle(self):
        with self.assertRaises(ValueError):
            verify_text(self.text.replace("Parsial: 2", "Parsial: 0"), [self.row])

    def test_changed_numeric_column(self):
        with self.assertRaises(ValueError):
            verify_text(self.text.replace("\n5\n", "\n0\n"), [self.row])


if __name__ == "__main__":
    unittest.main()
