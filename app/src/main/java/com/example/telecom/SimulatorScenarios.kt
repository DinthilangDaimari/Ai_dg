package com.example.telecom

import com.example.data.model.CallerProfile

data class SimulationScenario(
    val id: String,
    val title: String,
    val description: String,
    val callerProfile: CallerProfile,
    val initialStatement: String,
    val dialogueTurns: List<String>,
    val defaultCategory: String,
    val isSpamExpectation: Boolean = false
)

object SimulatorScenarios {

    val scenarios = listOf(
        SimulationScenario(
            id = "courier_delivery",
            title = "Amazon Courier Delivery",
            description = "Delivery driver waiting at the apartment gate needing delivery instructions.",
            callerProfile = CallerProfile(
                name = "Santosh (Amazon Courier)",
                phoneNumber = "+91 98200 44321",
                isContact = false,
                initialSpamRisk = false,
                initialCategory = "Delivery"
            ),
            initialStatement = "Hello, this is Santosh from Amazon delivery. I have a parcel for your flat. I am standing near the main gate security.",
            dialogueTurns = listOf(
                "Yes, the OTP is not required. Can I leave the package with the security guard at tower 2?",
                "Alright, guard Vinod has accepted the box. Marking delivered now. Have a nice day!"
            ),
            defaultCategory = "Delivery"
        ),
        SimulationScenario(
            id = "clinic_appointment",
            title = "Care Dental Clinic",
            description = "Clinic receptionist calling to confirm tomorrow's dental checkup slot.",
            callerProfile = CallerProfile(
                name = "Dr. Anita Dental Clinic",
                phoneNumber = "+91 80 2555 8899",
                isContact = true,
                initialSpamRisk = false,
                initialCategory = "Healthcare"
            ),
            initialStatement = "Good morning! This is Neha from Dr. Anita's Dental Clinic. We are calling to confirm your dental check-up scheduled for tomorrow at 3:30 PM.",
            dialogueTurns = listOf(
                "Great! Dr. Anita asked to remind you to bring your previous X-rays if you have them. Should we keep the slot confirmed?",
                "Wonderful, your appointment is confirmed for tomorrow 3:30 PM. See you then!"
            ),
            defaultCategory = "Healthcare"
        ),
        SimulationScenario(
            id = "tech_recruiter",
            title = "Tech Talent Recruiter",
            description = "Senior recruiter reaching out regarding an Android Staff Engineer position.",
            callerProfile = CallerProfile(
                name = "Vikram - Talent Acquisition",
                phoneNumber = "+91 97110 56789",
                isContact = false,
                initialSpamRisk = false,
                initialCategory = "Recruiter"
            ),
            initialStatement = "Hi there, my name is Vikram. I lead technical hiring at Zepto. I came across your GitHub profile and wanted to discuss a Staff Android position.",
            dialogueTurns = listOf(
                "The position is remote-friendly with team hubs in Bangalore. We can schedule a brief 15-minute chat whenever you are free this week.",
                "Sounds great. I will send the job description and my Calendly link over email. Thanks for speaking with me!"
            ),
            defaultCategory = "Recruiter"
        ),
        SimulationScenario(
            id = "loan_spam",
            title = "Personal Loan Telemarketer",
            description = "Aggressive telemarketer offering instant pre-approved personal loans with 0 documentation.",
            callerProfile = CallerProfile(
                name = "Suspected Spam • Bajaj Direct Loan",
                phoneNumber = "+91 140 882 9901",
                isContact = false,
                initialSpamRisk = true,
                initialCategory = "Spam/Robocall"
            ),
            initialStatement = "Congratulations sir! You have been pre-selected for an instant 10 lakh personal credit loan at special discount interest rates!",
            dialogueTurns = listOf(
                "Sir, this offer is valid only for today! Zero processing fee and funds credited in 10 minutes to your account!",
                "Are you sure you do not need emergency funds or a new credit card line?"
            ),
            defaultCategory = "Spam/Robocall",
            isSpamExpectation = true
        )
    )
}
